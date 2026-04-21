package com.finnode.account.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado al topic [account.funds-reservation-failed] cuando
 * account-service no puede completar la reserva de fondos.
 *
 * Causas posibles: saldo insuficiente, cuenta suspendida o cerrada,
 * o conflicto de Optimistic Locking no resuelto tras reintentos.
 *
 * Consumidor: payment-orchestrator — al recibirlo, cancela el flujo
 * e inicia la compensación del Patrón Saga para los pasos previos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundsReservationFailedEvent {

    /** Identificador de la transacción fallida — correlaciona los eventos del Saga. */
    private String transactionId;

    /** Cuenta bancaria en la que se intentó la reserva. */
    private UUID accountId;

    /**
     * Motivo del fallo. Valores posibles:
     * - INSUFFICIENT_FUNDS
     * - ACCOUNT_SUSPENDED
     * - ACCOUNT_NOT_FOUND
     * - CONCURRENCY_CONFLICT
     */
    private String reason;

    /** Momento exacto en que se determinó el fallo. */
    private Instant timestamp;
}