package com.finnode.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO de entrada para liberar una reserva de fondos (rollback del Patrón Saga).
 * Usado en: POST /accounts/{accountId}/release
 *
 * Se invoca cuando cualquier paso de la Saga falla después de la reserva.
 * El payment-orchestrator publica un evento de compensación a Kafka,
 * AccountEventConsumer lo recibe y llama a este endpoint para devolver
 * los fondos bloqueados al saldo disponible del usuario.
 *
 * Operación: reservedBalance -= amount  (balance no cambia)
 */
public record ReleaseReserveRequest(

        /**
         * ID de la transacción Saga a revertir.
         * Debe coincidir con el transactionId de la reserva original.
         * Garantiza que se libere exactamente la reserva correcta.
         */
        @NotBlank(message = "El transactionId es obligatorio")
        String transactionId,

        /**
         * Monto a liberar de la reserva.
         * Debe coincidir con el monto que fue reservado originalmente.
         */
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        BigDecimal amount
) {}