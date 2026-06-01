package com.mehmetali.ledger.domain.service;

import com.mehmetali.ledger.api.exception.PaymentNotFoundException;
import com.mehmetali.ledger.domain.model.Account;
import com.mehmetali.ledger.domain.model.AccountStatus;
import com.mehmetali.ledger.domain.model.AuditLog;
import com.mehmetali.ledger.domain.model.Transaction;
import com.mehmetali.ledger.domain.model.TransactionStatus;
import com.mehmetali.ledger.domain.model.TransactionType;
import com.mehmetali.ledger.domain.repository.AccountRepository;
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
    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final LedgerService ledgerService;
    private final SnapshotService snapshotService;
    private final PaymentEventProducer eventProducer;

    @Transactional
    public Transaction initiatePayment(Transaction transaction) {
        if (transaction.getFromAccountId().equals(transaction.getToAccountId())) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        Account from = accountRepository.findById(transaction.getFromAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + transaction.getFromAccountId()));
        Account to = accountRepository.findById(transaction.getToAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found: " + transaction.getToAccountId()));

        if (from.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Source account is not active");
        }
        if (to.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Destination account is not active");
        }
        if (!from.getCurrency().equals(transaction.getCurrency())) {
            throw new IllegalArgumentException(
                    "Currency mismatch: source account currency is " + from.getCurrency());
        }
        if (!to.getCurrency().equals(transaction.getCurrency())) {
            throw new IllegalArgumentException(
                    "Currency mismatch: destination account currency is " + to.getCurrency());
        }

        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setTransactionType(TransactionType.PAYMENT);
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

        if (tx.getTransactionType() == TransactionType.REVERSAL) {
            processReversal(tx);
        } else {
            processNormalPayment(tx);
        }
    }

    private void processNormalPayment(Transaction tx) {
        // Pessimistic lock on sender — serializes concurrent payments from the same account
        accountRepository.findByIdForUpdate(tx.getFromAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + tx.getFromAccountId()));

        BigDecimal senderBalance = snapshotService.calculateHybridBalance(tx.getFromAccountId());
        if (senderBalance.compareTo(tx.getAmount()) < 0) {
            transition(tx, TransactionStatus.FAILED, "Insufficient balance");
            return;
        }

        ledgerService.createDoubleEntry(tx);
        transition(tx, TransactionStatus.SETTLED, "Double-entry posted");
    }

    private void processReversal(Transaction tx) {
        // The "to" account is the original receiver — they are debited in the reversal
        accountRepository.findByIdForUpdate(tx.getToAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + tx.getToAccountId()));

        BigDecimal receiverBalance = snapshotService.calculateHybridBalance(tx.getToAccountId());
        if (receiverBalance.compareTo(tx.getAmount()) < 0) {
            transition(tx, TransactionStatus.FAILED, "Insufficient balance for reversal");
            return;
        }

        ledgerService.createReverseEntry(tx);
        transition(tx, TransactionStatus.SETTLED, "Reversal entries posted");

        if (tx.getOriginalTransactionId() != null) {
            Transaction original = findOrThrow(tx.getOriginalTransactionId());
            transition(original, TransactionStatus.REVERSED, "Reversed by tx: " + tx.getId());
        }
    }

    @Transactional
    public Transaction reversePayment(UUID transactionId, String idempotencyKey) {
        Transaction original = findOrThrow(transactionId);
        TransactionStateMachine.validate(original.getStatus(), TransactionStatus.REVERSED);

        Transaction reversal = new Transaction();
        reversal.setIdempotencyKey(idempotencyKey);
        reversal.setFromAccountId(original.getFromAccountId());
        reversal.setToAccountId(original.getToAccountId());
        reversal.setAmount(original.getAmount());
        reversal.setCurrency(original.getCurrency());
        reversal.setDescription("Reversal of " + original.getId());
        reversal.setTransactionType(TransactionType.REVERSAL);
        reversal.setOriginalTransactionId(original.getId());
        reversal.setStatus(TransactionStatus.PENDING);
        Transaction saved = transactionRepository.save(reversal);

        audit(saved.getId(), null, TransactionStatus.PENDING, "Reversal initiated for tx: " + original.getId());

        eventProducer.publish(new PaymentEvent(
                saved.getId(),
                saved.getFromAccountId(),
                saved.getToAccountId(),
                saved.getAmount(),
                saved.getCurrency()
        ));

        return saved;
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
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
