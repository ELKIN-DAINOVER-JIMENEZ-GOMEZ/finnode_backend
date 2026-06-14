package com.finnode.account.controller;

import com.finnode.account.dto.*;
import com.finnode.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // =========================================================================
    // CQRS Queries (Lecturas — sin transacciones)
    // =========================================================================

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable UUID accountId
    ) {
        return ResponseEntity.ok(accountService.getBalance(accountId));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountDetails(
            @PathVariable UUID accountId
    ) {
        return ResponseEntity.ok(accountService.getAccountDetails(accountId));
    }

    // =========================================================================
    // CQRS Commands (Escrituras — con transacciones)
    // =========================================================================

    @PostMapping("/{accountId}/reserve")
    public ResponseEntity<Void> reserveFunds(
            @PathVariable UUID accountId,
            @Valid @RequestBody ReserveFundsRequest request
    ) {
        accountService.reserveFunds(accountId, request.transactionId(), request.amount());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{accountId}/confirm-debit")
    public ResponseEntity<Void> confirmDebit(
            @PathVariable UUID accountId,
            @Valid @RequestBody ConfirmDebitRequest request
    ) {
        accountService.confirmDebit(accountId, request.transactionId(), request.amount());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{accountId}/credit")
    public ResponseEntity<Void> creditFunds(
            @PathVariable UUID accountId,
            @Valid @RequestBody CreditFundsRequest request
    ) {
        accountService.creditFunds(accountId, request.transactionId(), request.amount(), request.sourceAccountId());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{accountId}/release")
    public ResponseEntity<Void> releaseFunds(
            @PathVariable UUID accountId,
            @Valid @RequestBody ReleaseReserveRequest request
    ) {
        accountService.releaseFunds(accountId, request.transactionId(), request.amount());
        return ResponseEntity.accepted().build();
    }
}

