package com.mehmetali.ledger.api;

import com.mehmetali.ledger.api.dto.BalanceResponse;
import com.mehmetali.ledger.api.dto.LedgerEntryResponse;
import com.mehmetali.ledger.domain.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final LedgerService ledgerService;

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID id) {
        return ResponseEntity.ok(ledgerService.getBalanceResponse(id));
    }

    @GetMapping("/{id}/ledger")
    public ResponseEntity<Page<LedgerEntryResponse>> getLedger(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ledgerService.getLedger(id, pageable));
    }
}
