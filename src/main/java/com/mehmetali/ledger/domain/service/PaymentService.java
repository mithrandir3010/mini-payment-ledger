package com.mehmetali.ledger.domain.service;

import com.mehmetali.ledger.domain.model.AuditLog;
import com.mehmetali.ledger.domain.model.Transaction;
import com.mehmetali.ledger.domain.model.TransactionStatus;
import com.mehmetali.ledger.domain.repository.AuditLogRepository;
import com.mehmetali.ledger.domain.repository.TransactionRepository;
import com.mehmetali.ledger.domain.statemachine.TransactionStateMachine;
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
    private final AuditLogRepository auditLogRepository;
    private final LedgerService ledgerService;
    private final PaymentEventProducer eventProducer;

    @Transactional
    public Transaction initiatePayment(Transaction transaction) {
        transaction.setStatus(TransactionStatus.PENDING);
        Transaction saved = transactionRepository.save(transaction);
        audit(saved.getId(), null, TransactionStatus.PENDING, "Payment initiated");

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
        Transaction tx = findOrThrow(transactionId);

        TransactionStateMachine.validate(tx.getStatus(), TransactionStatus.PROCESSING);
        transition(tx, TransactionStatus.PROCESSING, "Kafka consumer picked up");

        BigDecimal senderBalance = ledgerService.getBalance(tx.getFromAccountId());
        if (senderBalance.compareTo(tx.getAmount()) < 0) {
            transition(tx, TransactionStatus.FAILED, "Insufficient balance");
            return;
        }

        ledgerService.createDoubleEntry(tx);
        transition(tx, TransactionStatus.SETTLED, "Double-entry posted");
    }

    @Transactional
    public Transaction reversePayment(UUID transactionId) {
        Transaction original = findOrThrow(transactionId);
        TransactionStateMachine.validate(original.getStatus(), TransactionStatus.REVERSED);

        Transaction reversal = new Transaction();
        reversal.setIdempotencyKey("reversal-" + original.getIdempotencyKey());
        reversal.setFromAccountId(original.getFromAccountId());
        reversal.setToAccountId(original.getToAccountId());
        reversal.setAmount(original.getAmount());
        reversal.setCurrency(original.getCurrency());
        reversal.setDescription("Reversal of " + original.getId());
        reversal.setStatus(TransactionStatus.SETTLED);
        Transaction savedReversal = transactionRepository.save(reversal);

        ledgerService.createReverseEntry(original, savedReversal);

        transition(original, TransactionStatus.REVERSED, "Reversed by reversal tx: " + savedReversal.getId());
        return savedReversal;
    }

    public Transaction getPayment(UUID id) {
        return findOrThrow(id);
    }

    private void transition(Transaction tx, TransactionStatus to, String reason) {
        TransactionStatus from = tx.getStatus();
        tx.setStatus(to);
        transactionRepository.save(tx);
        audit(tx.getId(), from, to, reason);
    }

    private void audit(UUID txId, TransactionStatus from, TransactionStatus to, String reason) {
        AuditLog log = new AuditLog();
        log.setTransactionId(txId);
        log.setFromStatus(from != null ? from.name() : null);
        log.setToStatus(to.name());
        log.setReason(reason);
        auditLogRepository.save(log);
    }

    private Transaction findOrThrow(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + id));
    }
}
