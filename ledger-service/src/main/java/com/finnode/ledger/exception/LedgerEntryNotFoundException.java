package com.finnode.ledger.exception;

import java.util.UUID;

/**
 * Excepción lanzada cuando no se encuentran asientos contables
 * para la cuenta o transacción solicitada.
 *
 * Casos de uso:
 *   - GET /ledger/{accountId}/entries    → cuenta sin asientos registrados
 *   - GET /ledger/{accountId}/balance    → cuenta sin asientos registrados
 *   - GET /ledger/transactions/{id}      → transacción no encontrada en el libro mayor
 *
 * El GlobalExceptionHandler la captura y retorna HTTP 404 con un
 * cuerpo JSON estructurado para que el frontend pueda manejarlo.
 *
 * Extiende RuntimeException (unchecked) para no forzar try/catch
 * en cada punto donde se lanza — Spring la captura automáticamente
 * a través del @RestControllerAdvice.
 */
public class LedgerEntryNotFoundException extends RuntimeException {

    private final UUID resourceId;

    /**
     * Constructor para cuentas sin asientos registrados.
     *
     * @param accountId ID de la cuenta que no tiene asientos en el libro mayor
     */
    public LedgerEntryNotFoundException(UUID accountId) {
        super("No se encontraron asientos contables para la cuenta: " + accountId);
        this.resourceId = accountId;
    }

    /**
     * Constructor para transacciones no encontradas.
     *
     * @param transactionId ID de la transacción que no existe en el libro mayor
     * @param isTransaction flag para diferenciar el mensaje (true = transacción, false = cuenta)
     */
    public LedgerEntryNotFoundException(UUID transactionId, boolean isTransaction) {
        super("No se encontraron asientos contables para la transacción: " + transactionId);
        this.resourceId = transactionId;
    }

    /**
     * Retorna el ID del recurso que no fue encontrado.
     * Usado por GlobalExceptionHandler para incluirlo en la respuesta JSON.
     *
     * @return UUID de la cuenta o transacción no encontrada
     */
    public UUID getResourceId() {
        return resourceId;
    }
}