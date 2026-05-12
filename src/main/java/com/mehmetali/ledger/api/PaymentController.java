package com.mehmetali.ledger.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mehmetali.ledger.api.dto.PaymentRequest;
import com.mehmetali.ledger.api.dto.PaymentResponse;
import com.mehmetali.ledger.domain.model.Transaction;
import com.mehmetali.ledger.domain.service.PaymentService;
import com.mehmetali.ledger.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<PaymentResponse> initiatePayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) throws JsonProcessingException {

        var cached = idempotencyService.getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
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

        return ResponseEntity.accepted().body(response);
    }

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
}
