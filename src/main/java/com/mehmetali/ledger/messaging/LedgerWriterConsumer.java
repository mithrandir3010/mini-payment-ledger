package com.mehmetali.ledger.messaging;

import com.mehmetali.ledger.domain.service.PaymentService;
import com.mehmetali.ledger.domain.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerWriterConsumer {

    private final PaymentService paymentService;
    private final SnapshotService snapshotService;

    @KafkaListener(topics = "payment.events", groupId = "ledger-writers")
    public void consume(PaymentEvent event) {
        log.info("Processing payment event: {}", event.transactionId());
        paymentService.processPayment(event.transactionId());

        // maybeTakeSnapshot runs AFTER processPayment commits — REQUIRES_NEW on takeSnapshot
        // ensures SERIALIZABLE isolation for the snapshot without affecting the payment tx.
        snapshotService.maybeTakeSnapshot(event.fromAccountId());
        snapshotService.maybeTakeSnapshot(event.toAccountId());
    }
}
