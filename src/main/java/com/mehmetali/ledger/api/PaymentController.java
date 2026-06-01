package com.mehmetali.ledger.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mehmetali.ledger.api.dto.PaymentRequest;
import com.mehmetali.ledger.api.dto.PaymentResponse;
import com.mehmetali.ledger.domain.model.Transaction;
import com.mehmetali.ledger.domain.service.PaymentService;
import com.mehmetali.ledger.idempotency.IdempotencyService;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Payments", description = "Payment initiation, status polling, and reversal")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Operation(summary = "Initiate a payment",
            description = "Saves PENDING transaction, publishes to Kafka. Returns 202 — not yet settled.")
    @ApiResponse(responseCode = "202", description = "Payment accepted for async processing")
    @ApiResponse(responseCode = "400", description = "Validation error or business rule violation")
    @Parameter(name = "Idempotency-Key", required = true,
            description = "Client-generated UUID — same key within 24h returns the cached response")
    @PostMapping
    public ResponseEntity<PaymentResponse> initiatePayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) throws JsonProcessingException {

        var cached = idempotencyService.getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            meterRegistry.counter("payments.initiated", "idempotency", "hit").increment();
            return ResponseEntity.accepted()
                    .body(objectMapper.readValue(cached.get(), PaymentResponse.class));
        }

        Transaction transaction = new Transaction();
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setFromAccountId(request.getFromAccountId());
        transaction.setToAccountId(request.getToAccountId());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setDescription(request.getDescription());

        Transaction result = paymentService.initiatePayment(transaction);

        PaymentResponse response = new PaymentResponse(
                result.getId(),
                result.getStatus().name(),
                "Payment accepted, processing asynchronously.",
                "/api/v1/payments/" + result.getId()
        );

        idempotencyService.saveResponse(idempotencyKey, objectMapper.writeValueAsString(response));
        meterRegistry.counter("payments.initiated", "idempotency", "miss").increment();

        return ResponseEntity.accepted().body(response);
    }

    @Operation(summary = "Get payment status")
    @ApiResponse(responseCode = "200", description = "Current transaction state")
    @ApiResponse(responseCode = "404", description = "Transaction not found")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID id) {
        Transaction result = paymentService.getPayment(id);

        PaymentResponse response = new PaymentResponse(
                result.getId(),
                result.getStatus().name(),
                null,
                "/api/v1/payments/" + result.getId()
        );

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Reverse a payment",
            description = "Creates a REVERSAL transaction. Original must be SETTLED.")
    @ApiResponse(responseCode = "202", description = "Reversal accepted for async processing")
    @ApiResponse(responseCode = "400", description = "Illegal state transition or insufficient balance")
    @ApiResponse(responseCode = "404", description = "Original transaction not found")
    @Parameter(name = "Idempotency-Key", required = true,
            description = "Client-generated UUID — same key within 24h returns the cached response")
    @PostMapping("/{id}/reverse")
    public ResponseEntity<PaymentResponse> reversePayment(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) throws JsonProcessingException {

        var cached = idempotencyService.getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.accepted()
                    .body(objectMapper.readValue(cached.get(), PaymentResponse.class));
        }

        Transaction reversal = paymentService.reversePayment(id, idempotencyKey);

        PaymentResponse response = new PaymentResponse(
                reversal.getId(),
                reversal.getStatus().name(),
                "Reversal accepted, processing asynchronously.",
                "/api/v1/payments/" + reversal.getId()
        );

        idempotencyService.saveResponse(idempotencyKey, objectMapper.writeValueAsString(response));

        return ResponseEntity.accepted().body(response);
    }
}
