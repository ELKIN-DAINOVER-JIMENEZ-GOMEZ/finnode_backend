package com.finnode.ledger.exception;

/**
 * Excepción lanzada cuando se detecta un desbalance contable.
 * Representa una violación de la invariante central del sistema:
 * en toda transacción, Σ DEBIT debe ser igual a Σ CREDIT.
 *
 * Casos de uso:
 *   - El monto del evento PaymentCompletedEvent es nulo, cero o negativo
 *   - El código de moneda del evento es nulo o vacío
 *   - Los montos del asiento DEBIT y CREDIT no coinciden (error en el orquestador)
 *
 * Cuando esta excepción se lanza desde LedgerService durante el procesamiento
 * de un evento Kafka:
 *   1. La transacción @Transactional se revierte → no se persiste ningún asiento
 *   2. LedgerEventConsumer NO llama ack.acknowledge() → Kafka reentrega el mensaje
 *   3. Tras 3 reintentos fallidos → el mensaje va a la DLQ para revisión manual
 *
 * Cuando se lanza desde un endpoint REST (improbable, pero posible):
 *   GlobalExceptionHandler la captura y retorna HTTP 422 Unprocessable Entity.
 *
 * HTTP 422 es más preciso que 400 para este caso:
 *   - 400 Bad Request → el cliente envió datos mal formados
 *   - 422 Unprocessable Entity → los datos están bien formados pero violan
 *     una regla de negocio (en este caso, la Partida Doble)
 */
public class LedgerImbalanceException extends RuntimeException {

    /**
     * @param message descripción del desbalance detectado, incluyendo
     *                el transactionId y los montos que no cuadran
     */
    public LedgerImbalanceException(String message) {
        super(message);
    }
}