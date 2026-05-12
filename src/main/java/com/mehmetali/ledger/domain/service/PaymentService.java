package com.mehmetali.ledger.domain.service;

import com.mehmetali.ledger.domain.model.Transaction;
import com.mehmetali.ledger.domain.model.TransactionStatus;
import com.mehmetali.ledger.domain.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;

    @Transactional
    public Transaction initiatePayment(Transaction transaction) {
        transaction.setStatus(TransactionStatus.PENDING);
        Transaction saved = transactionRepository.save(transaction);

        BigDecimal senderBalance = ledgerService.getBalance(saved.getFromAccountId());
        if (senderBalance.compareTo(saved.getAmount()) < 0) {
            throw new IllegalStateException("Insufficient balance for account: " + saved.getFromAccountId());
        }

        ledgerService.createDoubleEntry(saved);

        saved.setStatus(TransactionStatus.SETTLED);
        return transactionRepository.save(saved);
    }

    public Transaction getPayment(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + id));
    }
}
