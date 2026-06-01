package com.mehmetali.ledger.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mehmetali.ledger.api.dto.AccountRequest;
import com.mehmetali.ledger.api.dto.AccountResponse;
import com.mehmetali.ledger.api.dto.BalanceResponse;
import com.mehmetali.ledger.api.dto.LedgerEntryResponse;
import com.mehmetali.ledger.domain.model.Account;
import com.mehmetali.ledger.domain.service.AccountService;
import com.mehmetali.ledger.domain.service.BalanceService;
import com.mehmetali.ledger.domain.service.LedgerService;
import com.mehmetali.ledger.idempotency.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Accounts", description = "Account management, balance, and ledger history")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final BalanceService balanceService;
    private final LedgerService ledgerService;
    private final AccountService accountService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Create a new account")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @Parameter(name = "Idempotency-Key", required = true,
        description = "Client-generated UUID — same key within 24h returns cached response")
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AccountRequest request) throws JsonProcessingException {

        var cached = idempotencyService.getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.status(201)
                .body(objectMapper.readValue(cached.get(), AccountResponse.class));
        }

        Account account = new Account();
        account.setOwnerId(request.getOwnerId());
        account.setCurrency(request.getCurrency().toUpperCase());

        Account saved = accountService.createAccount(account);
        AccountResponse response = new AccountResponse(
            saved.getId(), saved.getOwnerId(), saved.getCurrency(),
            saved.getStatus().name(), saved.getCreatedAt()
        );

        idempotencyService.saveResponse(idempotencyKey, objectMapper.writeValueAsString(response));
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "Get account balance",
        description = "Hybrid strategy: Redis cache → snapshot+delta → full scan. "
            + "Response includes 'source' (CACHE|DB) for transparency.")
    @ApiResponse(responseCode = "200", description = "Balance with source and calculatedAt")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID id) {
        return ResponseEntity.ok(balanceService.getBalance(id));
    }

    @Operation(summary = "Get paginated ledger entries",
        description = "Returns all DEBIT/CREDIT entries for the account, newest first by default.")
    @ApiResponse(responseCode = "200", description = "Page of ledger entries")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @GetMapping("/{id}/ledger")
    public ResponseEntity<Page<LedgerEntryResponse>> getLedger(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ledgerService.getLedger(id, pageable));
    }
}
