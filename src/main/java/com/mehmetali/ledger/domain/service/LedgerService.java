package com.mehmetali.ledger.domain.service;

import com.mehmetali.ledger.domain.model.EntryType;
import com.mehmetali.ledger.domain.model.LedgerEntry;
import com.mehmetali.ledger.domain.model.Transaction;
import com.mehmetali.ledger.domain.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public BigDecimal getBalance(UUID accountId) {
        return ledgerEntryRepository.calculateBalance(accountId);
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
