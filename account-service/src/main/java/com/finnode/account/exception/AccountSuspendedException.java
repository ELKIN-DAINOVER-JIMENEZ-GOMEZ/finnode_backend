package com.finnode.account.exception;

import java.util.UUID;

public class AccountSuspendedException extends RuntimeException {

    public AccountSuspendedException(UUID accountId) {
        super("La cuenta no está activa: " + accountId);
    }
}
