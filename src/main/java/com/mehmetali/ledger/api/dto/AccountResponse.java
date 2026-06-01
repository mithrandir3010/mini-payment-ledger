package com.mehmetali.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class AccountResponse {

    private UUID id;
    private UUID ownerId;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
}
