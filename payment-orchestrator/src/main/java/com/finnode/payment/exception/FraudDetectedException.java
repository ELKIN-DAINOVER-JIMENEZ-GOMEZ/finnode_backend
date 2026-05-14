package com.finnode.payment.exception;

/**
 * Excepción lanzada cuando el motor de detección de fraude rechaza una transacción
 * porque el riesgo supera el umbral configurado.
 *
 * Mapeada a HTTP 422 Unprocessable Entity por GlobalExceptionHandler.
 * La respuesta incluye el riskScore para transparencia del usuario.
 */
public class FraudDetectedException extends RuntimeException {

    private final double riskScore;
    private final String reason;

    public FraudDetectedException(double riskScore, String reason) {
        super("Transacción rechazada por detección de fraude. Score: " + riskScore);
        this.riskScore = riskScore;
        this.reason = reason;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public String getReason() {
        return reason;
    }
}

