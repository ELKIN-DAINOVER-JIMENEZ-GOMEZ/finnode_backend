package com.finnode.payment.controller;

import com.finnode.payment.dto.TransactionStatusResponse;
import com.finnode.payment.dto.TransferRequest;
import com.finnode.payment.dto.TransferResponse;
import com.finnode.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> initiateTransfer(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        log.info("[CONTROLLER] POST /payments/transfer | userId={}", userId);

        TransferResponse response = paymentService.initiateTransfer(request, userId);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionStatusResponse> getTransactionStatus(
            @PathVariable UUID transactionId
    ) {
        log.info("[CONTROLLER] GET /payments/{}", transactionId);

        TransactionStatusResponse response = paymentService.getTransactionStatus(transactionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<TransactionStatusResponse>> getHistory(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        log.info("[CONTROLLER] GET /payments/history | userId={}", userId);

        List<TransactionStatusResponse> history = paymentService.getUserTransactionHistory(userId);
        return ResponseEntity.ok(history);
    }
}

