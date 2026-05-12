package com.mehmetali.ledger.domain.service;

import com.mehmetali.ledger.domain.model.Transaction;
import com.mehmetali.ledger.domain.model.TransactionStatus;
import com.mehmetali.ledger.domain.repository.TransactionRepository;
import com.mehmetali.ledger.messaging.PaymentEvent;
import com.mehmetali.ledger.messaging.PaymentEventProducer;
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
    private final PaymentEventProducer eventProducer;

    @Transactional
    public Transaction initiatePayment(Transaction transaction) {
        transaction.setStatus(TransactionStatus.PENDING);
        Transaction saved = transactionRepository.save(transaction);

        eventProducer.publish(new PaymentEvent(
                saved.getId(),
                saved.getFromAccountId(),
                saved.getToAccountId(),
                saved.getAmount(),
                saved.getCurrency()
        ));

        return saved;
    }

    @Transactional
    public void processPayment(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        BigDecimal senderBalance = ledgerService.getBalance(transaction.getFromAccountId());
        if (senderBalance.compareTo(transaction.getAmount()) < 0) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            return;
        }

        ledgerService.createDoubleEntry(transaction);
        transaction.setStatus(TransactionStatus.SETTLED);
        transactionRepository.save(transaction);
    }

    public Transaction getPayment(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + id));
    }
}
