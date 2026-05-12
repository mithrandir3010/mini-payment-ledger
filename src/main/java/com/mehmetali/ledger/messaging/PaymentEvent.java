package com.mehmetali.ledger.messaging;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentEvent(
        UUID transactionId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency
) {}
