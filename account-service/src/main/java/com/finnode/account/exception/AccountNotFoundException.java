package com.finnode.account.exception;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID accountId) {
        super("Cuenta no encontrada con id: " + accountId);
    }
}