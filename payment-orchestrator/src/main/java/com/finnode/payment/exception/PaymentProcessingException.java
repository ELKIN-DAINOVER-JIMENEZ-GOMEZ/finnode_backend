package com.finnode.payment.exception;

/**
 * Excepción lanzada cuando el Saga falla de forma irrecuperable.
 *
 * Significa que los fondos fueron reservados pero algo falló en pasos posteriores,
 * y se inició la secuencia de compensación.
 *
 * Mapeada a HTTP 500 Internal Server Error por GlobalExceptionHandler.
 */
public class PaymentProcessingException extends RuntimeException {

    private final String failureReason;

    public PaymentProcessingException(String failureReason) {
        super("Error procesando el pago. Razón: " + failureReason);
        this.failureReason = failureReason;
    }

    public PaymentProcessingException(String failureReason, Throwable cause) {
        super("Error procesando el pago. Razón: " + failureReason, cause);
        this.failureReason = failureReason;
    }

    public String getFailureReason() {
        return failureReason;
    }
}

