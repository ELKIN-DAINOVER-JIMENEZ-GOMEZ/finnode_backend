package com.finnode.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO de entrada para reservar fondos antes de ejecutar una transferencia.
 * Usado en: POST /accounts/{accountId}/reserve
 *
 * El payment-orchestrator llama este endpoint como primer paso del Patrón Saga.
 * Si la reserva es exitosa, publica FundsReservedEvent a Kafka para continuar.
 * Si falla, publica FundsReservationFailedEvent para iniciar compensación.
 */
public record ReserveFundsRequest(

        /**
         * Monto a bloquear en la cuenta del remitente.
         * Debe ser estrictamente mayor a 0.
         * Se usa BigDecimal para garantizar precisión financiera exacta.
         */
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        BigDecimal amount,

        /**
         * ID único de la transacción generado por el payment-orchestrator.
         * Permite al account-service identificar a qué Saga pertenece esta reserva
         * y garantizar idempotencia: si el mensaje se reenvía, no se reserva dos veces.
         */
        @NotBlank(message = "El transactionId es obligatorio")
        String transactionId
) {}