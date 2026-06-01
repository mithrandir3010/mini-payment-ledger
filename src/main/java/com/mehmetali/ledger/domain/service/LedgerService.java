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
import java.math.RoundingMode;
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
        Account from = accountRepository.findById(transaction.getFromAccountId())
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + transaction.getFromAccountId()));
        Account to = accountRepository.findById(transaction.getToAccountId())
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + transaction.getToAccountId()));

        save(transaction, transaction.getFromAccountId(), EntryType.DEBIT,
            transaction.getAmount(), from.getCurrency());

        BigDecimal creditAmount = transaction.getAmount()
            .multiply(transaction.getFxRate())
            .setScale(4, RoundingMode.HALF_UP);
        save(transaction, transaction.getToAccountId(), EntryType.CREDIT,
            creditAmount, to.getCurrency());
    }

    // reversal.fromAccountId = original sender (receives credit), reversal.toAccountId = original receiver (gets debited)
    public void createReverseEntry(Transaction reversal) {
        Account from = accountRepository.findById(reversal.getFromAccountId())
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + reversal.getFromAccountId()));
        Account to = accountRepository.findById(reversal.getToAccountId())
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + reversal.getToAccountId()));

        BigDecimal debitAmount = reversal.getAmount()
            .multiply(reversal.getFxRate())
            .setScale(4, RoundingMode.HALF_UP);
        save(reversal, reversal.getToAccountId(), EntryType.DEBIT, debitAmount, to.getCurrency());
        save(reversal, reversal.getFromAccountId(), EntryType.CREDIT, reversal.getAmount(), from.getCurrency());
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

    private void save(Transaction transaction, UUID accountId, EntryType type,
            BigDecimal amount, String currency) {
        LedgerEntry entry = new LedgerEntry();
        entry.setTransaction(transaction);
        entry.setAccountId(accountId);
        entry.setEntryType(type);
        entry.setAmount(amount);
        entry.setCurrency(currency);
        ledgerEntryRepository.save(entry);
    }
}
