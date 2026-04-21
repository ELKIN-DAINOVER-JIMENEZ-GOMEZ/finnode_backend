package com.finnode.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO de entrada para confirmar el débito definitivo de una transferencia.
 * Usado en: POST /accounts/{accountId}/confirm-debit
 *
 * Se invoca después de que el ledger-service registró los asientos contables.
 * En este punto el dinero sale definitivamente: balance baja y reservedBalance también.
 * Antes de esta llamada, los fondos estaban reservados pero no debitados.
 */
public record ConfirmDebitRequest(

        /**
         * ID de la transacción Saga correspondiente.
         * Debe coincidir con el transactionId usado en la reserva previa.
         */
        @NotBlank(message = "El transactionId es obligatorio")
        String transactionId,

        /**
         * Monto a debitar definitivamente.
         * Debe coincidir con el monto reservado originalmente.
         */
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        BigDecimal amount
) {}