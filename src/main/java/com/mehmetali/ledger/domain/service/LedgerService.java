package com.mehmetali.ledger.domain.service;

import com.mehmetali.ledger.api.dto.BalanceResponse;
import com.mehmetali.ledger.domain.model.Account;
import com.mehmetali.ledger.domain.model.EntryType;
import com.mehmetali.ledger.domain.model.LedgerEntry;
import com.mehmetali.ledger.domain.model.Transaction;
import com.mehmetali.ledger.domain.repository.AccountRepository;
import com.mehmetali.ledger.domain.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
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
        return new BalanceResponse(accountId, balance, account.getCurrency());
    }

    public void createDoubleEntry(Transaction transaction) {
        LedgerEntry debit = new LedgerEntry();
        debit.setTransaction(transaction);
        debit.setAccountId(transaction.getFromAccountId());
        debit.setEntryType(EntryType.DEBIT);
        debit.setAmount(transaction.getAmount());
        debit.setCurrency(transaction.getCurrency());

        LedgerEntry credit = new LedgerEntry();
        credit.setTransaction(transaction);
        credit.setAccountId(transaction.getToAccountId());
        credit.setEntryType(EntryType.CREDIT);
        credit.setAmount(transaction.getAmount());
        credit.setCurrency(transaction.getCurrency());

        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);
    }
}
