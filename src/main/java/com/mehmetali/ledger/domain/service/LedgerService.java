package com.mehmetali.ledger.domain.service;

import com.mehmetali.ledger.api.dto.BalanceResponse;
import com.mehmetali.ledger.api.dto.LedgerEntryResponse;
import com.mehmetali.ledger.domain.model.Account;
import com.mehmetali.ledger.domain.model.EntryType;
import com.mehmetali.ledger.domain.model.LedgerEntry;
import com.mehmetali.ledger.domain.model.Transaction;
import com.mehmetali.ledger.domain.repository.AccountRepository;
import com.mehmetali.ledger.domain.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;

    public BigDecimal getBalance(UUID accountId) {
        return ledgerEntryRepository.calculateBalance(accountId);
    }

    public BalanceResponse getBalanceResponse(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        BigDecimal balance = ledgerEntryRepository.calculateBalance(accountId);
        return new BalanceResponse(accountId, balance, account.getCurrency(), "DB", java.time.LocalDateTime.now());
    }

    public void createDoubleEntry(Transaction transaction) {
        save(transaction, transaction.getFromAccountId(), EntryType.DEBIT);
        save(transaction, transaction.getToAccountId(), EntryType.CREDIT);
    }

    public void createReverseEntry(Transaction original, Transaction reversal) {
        save(reversal, original.getToAccountId(), EntryType.DEBIT);
        save(reversal, original.getFromAccountId(), EntryType.CREDIT);
    }

    public Page<LedgerEntryResponse> getLedger(UUID accountId, Pageable pageable) {
        return ledgerEntryRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(e -> new LedgerEntryResponse(
                        e.getId(),
                        e.getTransaction().getId(),
                        e.getEntryType(),
                        e.getAmount(),
                        e.getCurrency(),
                        e.getCreatedAt()
                ));
    }

    private void save(Transaction transaction, UUID accountId, EntryType type) {
        LedgerEntry entry = new LedgerEntry();
        entry.setTransaction(transaction);
        entry.setAccountId(accountId);
        entry.setEntryType(type);
        entry.setAmount(transaction.getAmount());
        entry.setCurrency(transaction.getCurrency());
        ledgerEntryRepository.save(entry);
    }
}
