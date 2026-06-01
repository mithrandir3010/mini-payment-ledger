package com.mehmetali.ledger.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationConsumer {

    @KafkaListener(topics = "payment.notifications", groupId = "notifiers")
    public void consume(PaymentEvent event) {
        // Placeholder: in production, dispatch push/email/SMS via notification service
        log.info("Notification event received for transaction={} from={} to={} amount={} {}",
                event.transactionId(),
                event.fromAccountId(),
                event.toAccountId(),
                event.amount(),
                event.currency());
    }
}
