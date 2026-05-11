package com.finnode.ledger.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento consumido desde el topic Kafka [payment.completed].
 * Publicado por el payment-orchestrator cuando una transferencia
 * fue aprobada por el motor de fraude y los fondos fueron reservados
 * exitosamente en el account-service.
 *
 * Al recibir este evento, LedgerService crea exactamente DOS asientos:
 *   - DEBIT  en la cuenta origen       (sourceAccountId)
 *   - CREDIT en la cuenta destino      (destinationAccountId)
 *
 * Ambos asientos se persisten en una sola @Transactional.
 * Si falla la persistencia, el mensaje vuelve a Kafka para reintento.
 *
 * IDEMPOTENCIA: antes de crear asientos, LedgerService verifica si ya
 * existe un registro con este transactionId. Si existe, el evento se
 * descarta silenciosamente sin crear duplicados.
 *
 * Ejemplo de payload JSON que llega desde Kafka:
 * {
 *   "transactionId":       "txn-9f1e2a3b-4c5d-6e7f-8a9b-0c1d2e3f4a5b",
 *   "sourceAccountId":     "acc-1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
 *   "destinationAccountId":"acc-5e6f7a8b-9c0d-1e2f-3a4b-5c6d7e8f9a0b",
 *   "amount":              500000.00,
 *   "currency":            "COP",
 *   "timestamp":           "2025-01-15T10:31:10Z"
 * }
 */
public record PaymentCompletedEvent(

        /**
         * Identificador único de la transacción.
         * Generado por el payment-orchestrator al iniciar el flujo Saga.
         * Es la clave de idempotencia: si ya existe en ledger_entries, se ignora.
         */
        UUID transactionId,

        /**
         * Cuenta que envió los fondos.
         * Se creará un asiento DEBIT asociado a esta cuenta.
         */
        UUID sourceAccountId,

        /**
         * Cuenta que recibe los fondos.
         * Se creará un asiento CREDIT asociado a esta cuenta.
         */
        UUID destinationAccountId,

        /**
         * Monto de la transferencia. Siempre positivo.
         * Se registra igual en ambos asientos (DEBIT y CREDIT deben cuadrar).
         */
        BigDecimal amount,

        /**
         * Código de moneda ISO 4217.
         * Ejemplo: COP, USD, EUR.
         */
        String currency,

        /**
         * Timestamp en que el payment-orchestrator completó la transacción.
         * Se usa como referencia temporal del movimiento financiero.
         */
        Instant timestamp

) {}