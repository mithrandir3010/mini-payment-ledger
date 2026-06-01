package com.mehmetali.ledger.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqConsumer {

    private final MeterRegistry meterRegistry;

    @KafkaListener(
            topics = "payment.dlq",
            groupId = "dlq-handlers",
            containerFactory = "dlqKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record) {
        meterRegistry.counter("payment.dlq.received").increment();

        String originalTopic = headerValue(record, "kafka_original-topic");
        String exceptionMessage = headerValue(record, "kafka_exception-message");
        String exceptionClass = headerValue(record, "kafka_exception-fqcn");

        log.error(
                "DLQ message received — key={} originalTopic={} exception={} exceptionClass={} payload={}",
                record.key(),
                originalTopic,
                exceptionMessage,
                exceptionClass,
                record.value()
        );
    }

    private String headerValue(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) {
            return "N/A";
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
