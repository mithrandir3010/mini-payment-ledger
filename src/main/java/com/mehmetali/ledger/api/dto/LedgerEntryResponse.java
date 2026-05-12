package com.mehmetali.ledger.api.dto;

import com.mehmetali.ledger.domain.model.EntryType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class LedgerEntryResponse {

    private UUID id;
    private UUID transactionId;
    private EntryType entryType;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime createdAt;
}
