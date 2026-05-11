package com.finnode.ledger.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado al topic Kafka [ledger.entries-recorded].
 * Lo publica LedgerEventPublisher una vez que los asientos contables
 * quedaron persistidos exitosamente en la base de datos.
 *
 * Consumidor: payment-orchestrator.
 * Al recibir este evento, el orquestador sabe que el paso contable
 * del Patrón Saga fue exitoso y puede avanzar al siguiente paso
 * (confirmación del pago y notificación al usuario vía WebSocket).
 *
 * Si el payment-orchestrator no recibe este evento dentro del timeout
 * configurado, asume que el ledger-service falló y publica un
 * PaymentReversedEvent para compensar la operación completa.
 *
 * Ejemplo de payload JSON publicado a Kafka:
 * {
 *   "transactionId":    "txn-9f1e2a3b-4c5d-6e7f-8a9b-0c1d2e3f4a5b",
 *   "debitEntryId":     "entry-aaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
 *   "creditEntryId":    "entry-1111-2222-3333-4444-555555555555",
 *   "amount":           500000.00,
 *   "currency":         "COP",
 *   "reversalConfirmed":false,
 *   "timestamp":        "2025-01-15T10:31:12Z"
 * }
 */
public record LedgerEntriesRecordedEvent(

        /**
         * ID de la transacción original.
         * Permite al payment-orchestrator correlacionar este evento
         * con el Saga que está coordinando.
         */
        UUID transactionId,

        /**
         * ID del asiento DEBIT creado en la cuenta origen.
         * Null si este evento confirma una reversión (reversalConfirmed = true).
         */
        UUID debitEntryId,

        /**
         * ID del asiento CREDIT creado en la cuenta destino.
         * Null si este evento confirma una reversión (reversalConfirmed = true).
         */
        UUID creditEntryId,

        /**
         * Monto registrado en los asientos contables.
         * Debe coincidir con el monto del PaymentCompletedEvent original.
         */
        BigDecimal amount,

        /**
         * Código de moneda ISO 4217 de los asientos registrados.
         */
        String currency,

        /**
         * Indica si este evento confirma el registro de asientos de reversión
         * (REVERSAL_DEBIT + REVERSAL_CREDIT) en lugar de asientos normales.
         *
         * false → asientos normales registrados (DEBIT + CREDIT)
         *         el Saga puede continuar hacia la confirmación del pago.
         *
         * true  → asientos de compensación registrados (REVERSAL_DEBIT + REVERSAL_CREDIT)
         *         el Saga registra la reversión como completada.
         */
        boolean reversalConfirmed,

        /**
         * Timestamp exacto en que los asientos fueron persistidos.
         */
        Instant timestamp

) {}