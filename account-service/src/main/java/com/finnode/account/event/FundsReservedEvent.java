package com.finnode.account.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado al topic [account.funds-reserved] cuando account-service
 * bloquea exitosamente el monto solicitado en la cuenta del remitente.
 *
 * Consumidor: payment-orchestrator — al recibirlo, continúa con el
 * siguiente paso del Patrón Saga (registro de asientos en ledger-service).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundsReservedEvent {

    /** Identificador de la transacción en curso — correlaciona todos los eventos del Saga. */
    private String transactionId;

    /** Cuenta bancaria desde la cual se reservaron los fondos. */
    private UUID accountId;

    /** Monto que fue bloqueado en reservedBalance. */
    private BigDecimal amount;

    /** Momento exacto en que la reserva fue persistida exitosamente. */
    private Instant timestamp;
}