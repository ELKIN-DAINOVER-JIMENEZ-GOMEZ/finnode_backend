package com.finnode.account.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, BigDecimal available, BigDecimal requested) {
        super(String.format(
                "Fondos insuficientes en cuenta %s — disponible: %s, solicitado: %s",
                accountId, available, requested
        ));
    }
}