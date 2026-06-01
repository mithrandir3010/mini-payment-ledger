package com.mehmetali.ledger.infrastructure;

import com.mehmetali.ledger.domain.model.AccountStatus;
import com.mehmetali.ledger.domain.repository.AccountRepository;
import com.mehmetali.ledger.domain.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotScheduler {

    private final SnapshotService snapshotService;
    private final AccountRepository accountRepository;

    /**
     * Günde bir kez, gece 02:00'de tüm aktif hesaplar için snapshot alır.
     * Yoğun saatlerin dışında çalışır — full SUM sorguları bu pencerede tolere edilir.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void dailySnapshot() {
        log.info("Daily snapshot job started");
        accountRepository.findByStatus(AccountStatus.ACTIVE).forEach(account -> {
            try {
                snapshotService.takeSnapshot(account.getId());
            } catch (Exception e) {
                log.error("Snapshot failed for account={}", account.getId(), e);
            }
        });
        log.info("Daily snapshot job completed");
    }
}
