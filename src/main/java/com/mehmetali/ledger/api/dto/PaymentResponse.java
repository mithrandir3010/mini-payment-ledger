package com.mehmetali.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class PaymentResponse {

    private UUID transactionId;
    private String status;
    private String message;
    private String statusUrl;
}
