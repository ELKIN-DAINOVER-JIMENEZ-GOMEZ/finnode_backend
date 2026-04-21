package com.finnode.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import java.math.BigDecimal;

/**
 * DTO de entrada para acreditar fondos a la cuenta del destinatario.
 * Usado en: POST /accounts/{accountId}/credit
 *
 * Es el paso final de una transferencia exitosa en el Patrón Saga.
 * Suma el monto al balance del destinatario sin tocar reservedBalance
 * (el destinatario recibe dinero libre, no bloqueado).
 */
public record CreditFundsRequest(

        /**
         * Monto a acreditar en la cuenta del destinatario.
         * Debe ser estrictamente positivo.
         */
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        BigDecimal amount,

        /**
         * ID de la transacción Saga.
         * Permite rastrear el crédito hasta la transferencia original
         * y garantizar idempotencia ante reenvíos de Kafka.
         */
        @NotBlank(message = "El transactionId es obligatorio")
        String transactionId,

        /**
         * ID de la cuenta origen del dinero.
         * Útil para auditoría y para que el ledger-service pueda
         * registrar el asiento contable de crédito con su contrapartida.
         */
        @NotNull(message = "El sourceAccountId es obligatorio")
        UUID sourceAccountId
) {}