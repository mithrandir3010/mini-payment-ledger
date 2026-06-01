package com.mehmetali.ledger.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class AccountRequest {

    @NotNull
    private UUID ownerId;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;
}
