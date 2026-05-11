package com.finnode.ledger.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento consumido desde el topic Kafka [payment.reversed].
 * Publicado por el payment-orchestrator cuando el Patrón Saga detecta
 * un fallo en algún paso posterior a la reserva de fondos y ordena
 * revertir la operación completa.
 *
 * Al recibir este evento, LedgerService crea exactamente DOS asientos
 * de compensación que neutralizan matemáticamente los asientos originales:
 *   - REVERSAL_CREDIT en la cuenta origen      (sourceAccountId)     → devuelve los fondos
 *   - REVERSAL_DEBIT  en la cuenta destino     (destinationAccountId) → retira los fondos
 *
 * RESULTADO NETO después de los 4 asientos (originales + reversión):
 *   sourceAccountId:      -amount (DEBIT) + amount (REVERSAL_CREDIT) = 0 ✅
 *   destinationAccountId: +amount (CREDIT) - amount (REVERSAL_DEBIT) = 0 ✅
 *
 * Los asientos originales NUNCA se eliminan. La reversión crea nuevos
 * asientos que contrarrestan los anteriores. Esto preserva la trazabilidad
 * completa para auditoría financiera.
 *
 * IDEMPOTENCIA: LedgerService verifica si ya existen asientos de reversión
 * para este transactionId antes de crear nuevos. Si existen, el evento
 * se descarta sin crear duplicados.
 *
 * Ejemplo de payload JSON que llega desde Kafka:
 * {
 *   "transactionId":       "txn-9f1e2a3b-4c5d-6e7f-8a9b-0c1d2e3f4a5b",
 *   "sourceAccountId":     "acc-1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
 *   "destinationAccountId":"acc-5e6f7a8b-9c0d-1e2f-3a4b-5c6d7e8f9a0b",
 *   "amount":              500000.00,
 *   "currency":            "COP",
 *   "reversalReason":      "ACCOUNT_SERVICE_TIMEOUT",
 *   "timestamp":           "2025-01-15T10:31:45Z"
 * }
 */
public record PaymentReversedEvent(

        /**
         * Identificador de la transacción que se está revirtiendo.
         * Debe coincidir con el transactionId de los asientos originales
         * (DEBIT/CREDIT) ya registrados en ledger_entries.
         * Es la clave para encontrar la transacción original y la clave
         * de idempotencia para no procesar la reversión dos veces.
         */
        UUID transactionId,

        /**
         * Cuenta que había enviado los fondos.
         * Recibirá un asiento REVERSAL_CREDIT para que le sean devueltos.
         */
        UUID sourceAccountId,

        /**
         * Cuenta que había recibido los fondos.
         * Recibirá un asiento REVERSAL_DEBIT para retirar los fondos acreditados.
         */
        UUID destinationAccountId,

        /**
         * Monto a revertir. Debe coincidir exactamente con el monto
         * de los asientos originales para que el libro contable cuadre.
         */
        BigDecimal amount,

        /**
         * Código de moneda ISO 4217.
         * Debe coincidir con la moneda de los asientos originales.
         */
        String currency,

        /**
         * Razón técnica de la reversión publicada por el payment-orchestrator.
         * Se almacena en la descripción de los asientos de compensación
         * para trazabilidad y auditoría.
         *
         * Valores posibles publicados por el payment-orchestrator:
         *   ACCOUNT_SERVICE_TIMEOUT    → el account-service no respondió a tiempo
         *   LEDGER_SERVICE_TIMEOUT     → este mismo servicio no confirmó a tiempo
         *   FRAUD_DETECTION_REJECTED   → el motor de IA rechazó la transacción post-reserva
         *   INSUFFICIENT_FUNDS         → la validación final de saldo falló
         *   SAGA_COMPENSATION_TRIGGERED → compensación manual del orquestador
         */
        String reversalReason,

        /**
         * Timestamp en que el payment-orchestrator ordenó la reversión.
         */
        Instant timestamp

) {}