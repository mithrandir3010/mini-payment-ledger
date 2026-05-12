package com.mehmetali.ledger.messaging;

import com.mehmetali.ledger.domain.model.Transaction;
import com.mehmetali.ledger.domain.model.TransactionStatus;
import com.mehmetali.ledger.domain.repository.TransactionRepository;
import com.mehmetali.ledger.domain.service.BalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceProjectorConsumer {

    private final BalanceService balanceService;
    private final TransactionRepository transactionRepository;

    @KafkaListener(topics = "payment.events", groupId = "balance-projectors")
    public void consume(PaymentEvent event) {
        Transaction tx = transactionRepository.findById(event.transactionId()).orElse(null);

        if (tx == null || tx.getStatus() != TransactionStatus.SETTLED) {
            // LedgerWriter henüz settle etmemiş — cache'i invalidate et,
            // bir sonraki getBalance DB'den tazeler.
            balanceService.invalidateCache(event.fromAccountId());
            balanceService.invalidateCache(event.toAccountId());
            log.debug("TX {} not yet SETTLED, cache invalidated", event.transactionId());
            return;
        }

        balanceService.updateCache(event.fromAccountId());
        balanceService.updateCache(event.toAccountId());
        log.info("Balance cache updated for accounts {} and {}", event.fromAccountId(), event.toAccountId());
    }
}
