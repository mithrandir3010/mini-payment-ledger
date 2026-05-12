package com.mehmetali.ledger.domain.statemachine;

import com.mehmetali.ledger.domain.model.TransactionStatus;

import java.util.Map;
import java.util.Set;

public class TransactionStateMachine {

    private static final Map<TransactionStatus, Set<TransactionStatus>> ALLOWED = Map.of(
            TransactionStatus.PENDING,    Set.of(TransactionStatus.PROCESSING, TransactionStatus.FAILED),
            TransactionStatus.PROCESSING, Set.of(TransactionStatus.SETTLED, TransactionStatus.FAILED),
            TransactionStatus.SETTLED,    Set.of(TransactionStatus.REVERSED),
            TransactionStatus.FAILED,     Set.of(TransactionStatus.REVERSED),
            TransactionStatus.REVERSED,   Set.of()
    );

    public static void validate(TransactionStatus from, TransactionStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException(
                    "Invalid transition: " + from + " -> " + to);
        }
    }
}
