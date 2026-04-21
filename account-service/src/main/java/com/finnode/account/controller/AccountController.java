package com.finnode.account.controller;

import com.finnode.account.dto.ReleaseReserveRequest;
import com.finnode.account.dto.ReserveFundsRequest;
import com.finnode.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/{accountId}/reserve")
    public ResponseEntity<Void> reserveFunds(
            @PathVariable UUID accountId,
            @Valid @RequestBody ReserveFundsRequest request
    ) {
        accountService.reserveFunds(accountId, request.transactionId(), request.amount());
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

