package com.mehmetali.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class BalanceResponse {

    private UUID accountId;
    private BigDecimal balance;
    private String currency;
    private String source;        // "DB" | "CACHE"
    private LocalDateTime calculatedAt;
}
