package com.mehmetali.ledger.domain.service;

import com.mehmetali.ledger.domain.model.AccountSnapshot;
import com.mehmetali.ledger.domain.repository.AccountSnapshotRepository;
import com.mehmetali.ledger.domain.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private static final long SNAPSHOT_THRESHOLD = 1000L;

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountSnapshotRepository snapshotRepository;

    /**
     * Race condition çözümü: SERIALIZABLE isolation seviyesi.
     *
     * Senaryo: snapshot alınırken eş zamanlı bir ödeme geldiğinde ne olur?
     *
     * 1. Bu transaction başladığında DB'nin o anki tutarlı görünümünü kilitler.
     * 2. calculateBalance() o görünüm üzerinden çalışır — snapshot_at = now().
     * 3. Eş zamanlı ödeme ya bu transaction tamamlanana kadar bekler,
     *    ya da başarısız olup retry atar.
     * 4. Sonuç: snapshot'a dahil edilmesi gereken hiçbir entry kaçmaz.
     *    created_at > snapshotted_at olan tüm yeni entryler delta sorgusuna girer.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void takeSnapshot(UUID accountId) {
        LocalDateTime snapAt = LocalDateTime.now();
        BigDecimal balance = ledgerEntryRepository.calculateBalance(accountId);
        long entryCount = ledgerEntryRepository.countByAccountId(accountId);

        AccountSnapshot snapshot = new AccountSnapshot();
        snapshot.setAccountId(accountId);
        snapshot.setBalance(balance);
        snapshot.setSnapshottedAt(snapAt);
        snapshot.setEntryCount(entryCount);
        snapshotRepository.save(snapshot);

        log.info("Snapshot taken for account={} balance={} entries={}", accountId, balance, entryCount);
    }

    /**
     * Hibrit bakiye hesaplama:
     *   balance = snapshot.balance + SUM(delta entries since snapshot)
     *
     * 100M satırlık bir tabloda bile bu sorgu hızlıdır çünkü:
     * - Snapshot bakiyesi hazır, tam SUM yapılmıyor.
     * - Delta sorgusu sadece snapshot'tan sonraki birkaç yüz/bin satırı tarar.
     * - ledger_entries(account_id, created_at) composite index'i bu sorguya tam uyar.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateHybridBalance(UUID accountId) {
        Optional<AccountSnapshot> latest =
                snapshotRepository.findTopByAccountIdOrderBySnapshottedAtDesc(accountId);

        if (latest.isEmpty()) {
            log.debug("No snapshot found for account={}, falling back to full scan", accountId);
            return ledgerEntryRepository.calculateBalance(accountId);
        }

        AccountSnapshot snapshot = latest.get();
        BigDecimal delta = ledgerEntryRepository.calculateBalanceAfter(accountId, snapshot.getSnapshottedAt());
        return snapshot.getBalance().add(delta);
    }

    /**
     * Delta entry sayısı SNAPSHOT_THRESHOLD'u aşarsa otomatik snapshot alır.
     * PaymentService.processPayment() içinden çağrılabilir.
     */
    @Transactional
    public void maybeTakeSnapshot(UUID accountId) {
        Optional<AccountSnapshot> latest =
                snapshotRepository.findTopByAccountIdOrderBySnapshottedAtDesc(accountId);

        long deltaCount = latest
                .map(s -> ledgerEntryRepository.countByAccountIdAndCreatedAtAfter(accountId, s.getSnapshottedAt()))
                .orElseGet(() -> ledgerEntryRepository.countByAccountId(accountId));

        if (deltaCount >= SNAPSHOT_THRESHOLD) {
            log.info("Delta threshold reached ({}) for account={}, taking snapshot", deltaCount, accountId);
            takeSnapshot(accountId);
        }
    }
}
