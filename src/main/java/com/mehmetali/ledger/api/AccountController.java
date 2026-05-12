package com.mehmetali.ledger.api;

import com.mehmetali.ledger.api.dto.BalanceResponse;
import com.mehmetali.ledger.domain.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final LedgerService ledgerService;

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID id) {
        BigDecimal balance = ledgerService.getBalance(id);
        return ResponseEntity.ok(new BalanceResponse(id, balance, null));
    }
}
