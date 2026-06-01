package com.mehmetali.ledger.api;

import com.mehmetali.ledger.api.dto.BalanceResponse;
import com.mehmetali.ledger.api.dto.LedgerEntryResponse;
import com.mehmetali.ledger.domain.service.BalanceService;
import com.mehmetali.ledger.domain.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Accounts", description = "Account balance and ledger history")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final BalanceService balanceService;
    private final LedgerService ledgerService;

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
