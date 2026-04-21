package com.finnode.account.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado al topic [account.funds-released] como confirmación de que
 * la compensación del Patrón Saga fue ejecutada exitosamente.
 *
 * Se publica luego de que account-service decrementa el reservedBalance
 * al recibir un evento [payment.reverse] del payment-orchestrator,
 * devolviendo los fondos bloqueados al saldo disponible del remitente.
 *
 * Consumidor: payment-orchestrator — lo usa para cerrar el ciclo de
 * compensación y marcar la transacción como revertida.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundsReleasedEvent {

    /** Identificador de la transacción que fue revertida — correlaciona los eventos del Saga. */
    private String transactionId;

    /** Cuenta bancaria en la que se liberaron los fondos previamente reservados. */
    private UUID accountId;

    /** Monto devuelto al saldo disponible (decrementado de reservedBalance). */
    private BigDecimal amount;

    /** Momento exacto en que la liberación fue persistida exitosamente. */
    private Instant timestamp;
}