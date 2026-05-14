package com.finnode.payment.exception;

/**
 * Excepción lanzada cuando se intenta acceder a una transacción que no existe.
 *
 * Mapeada a HTTP 404 Not Found por GlobalExceptionHandler.
 */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String transactionId) {
        super("Transacción no encontrada: " + transactionId);
    }

    public TransactionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

