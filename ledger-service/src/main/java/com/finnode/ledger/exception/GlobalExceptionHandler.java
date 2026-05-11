package  com.finnode.ledger.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Map;

/**
 * Manejador global de excepciones para todos los controladores REST.
 *
 * Centraliza la conversión de excepciones en respuestas HTTP con un
 * cuerpo JSON uniforme. Esto garantiza que el frontend siempre reciba
 * el mismo formato de error sin importar qué excepción se lanzó.
 *
 * Formato estándar de respuesta de error:
 * {
 *   "timestamp": "2025-01-15T10:31:12Z",
 *   "status":    404,
 *   "error":     "NOT_FOUND",
 *   "message":   "No se encontraron asientos contables para la cuenta: acc-1a2b...",
 *   "resourceId": "acc-1a2b3c4d-..."   ← solo en errores 404
 * }
 *
 * @RestControllerAdvice intercepta las excepciones antes de que Spring
 * las convierta en respuestas 500 genéricas, permitiendo retornar el
 * código HTTP correcto con un mensaje de error descriptivo.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maneja cuentas o transacciones sin asientos en el libro mayor.
     * Retorna HTTP 404 Not Found.
     *
     * Ejemplo de respuesta:
     * {
     *   "timestamp":  "2025-01-15T10:31:12Z",
     *   "status":     404,
     *   "error":      "NOT_FOUND",
     *   "message":    "No se encontraron asientos contables para la cuenta: acc-1a2b3c4d",
     *   "resourceId": "acc-1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"
     * }
     */
    @ExceptionHandler(LedgerEntryNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleLedgerEntryNotFound(
            LedgerEntryNotFoundException ex
    ) {
        log.warn("[EXCEPTION] LedgerEntryNotFoundException: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "timestamp",  Instant.now().toString(),
                "status",     HttpStatus.NOT_FOUND.value(),
                "error",      "NOT_FOUND",
                "message",    ex.getMessage(),
                "resourceId", ex.getResourceId().toString()
        ));
    }

    /**
     * Maneja violaciones de la Contabilidad de Partida Doble.
     * Retorna HTTP 422 Unprocessable Entity.
     *
     * Este error indica que los datos del evento Kafka son semánticamente
     * inválidos (montos que no cuadran, moneda nula, monto negativo).
     * No es un error del cliente REST — es un error en el evento publicado
     * por el payment-orchestrator.
     *
     * Ejemplo de respuesta:
     * {
     *   "timestamp": "2025-01-15T10:31:12Z",
     *   "status":    422,
     *   "error":     "LEDGER_IMBALANCE",
     *   "message":   "Desbalance contable en transacción: txn-9f1e2a3b | debit=500000 | credit=499999"
     * }
     */
    @ExceptionHandler(LedgerImbalanceException.class)
    public ResponseEntity<Map<String, Object>> handleLedgerImbalance(
            LedgerImbalanceException ex
    ) {
        log.error("[EXCEPTION] LedgerImbalanceException — posible corrupción de evento Kafka: {}",
                ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status",    HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "error",     "LEDGER_IMBALANCE",
                "message",   ex.getMessage()
        ));
    }

    /**
     * Maneja parámetros de ruta o query con formato inválido.
     * Retorna HTTP 400 Bad Request.
     *
     * Ejemplo: GET /ledger/no-es-un-uuid/entries
     * El controlador espera UUID pero recibe un String inválido.
     *
     * Ejemplo de respuesta:
     * {
     *   "timestamp": "2025-01-15T10:31:12Z",
     *   "status":    400,
     *   "error":     "BAD_REQUEST",
     *   "message":   "Parámetro inválido: 'accountId' debe ser un UUID válido"
     * }
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {
        log.warn("[EXCEPTION] MethodArgumentTypeMismatchException: parámetro={} | valor={}",
                ex.getName(), ex.getValue());

        String message = String.format(
                "Parámetro inválido: '%s' debe ser un %s válido",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "valor"
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status",    HttpStatus.BAD_REQUEST.value(),
                "error",     "BAD_REQUEST",
                "message",   message
        ));
    }

    /**
     * Fallback: captura cualquier excepción no manejada explícitamente.
     * Retorna HTTP 500 Internal Server Error sin exponer detalles internos.
     *
     * El stack trace completo se registra en los logs para diagnóstico,
     * pero el mensaje de respuesta al cliente es genérico para no
     * exponer información sensible de la infraestructura.
     *
     * Ejemplo de respuesta:
     * {
     *   "timestamp": "2025-01-15T10:31:12Z",
     *   "status":    500,
     *   "error":     "INTERNAL_SERVER_ERROR",
     *   "message":   "Error interno del servidor. Contacte al equipo de soporte."
     * }
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("[EXCEPTION] Error no controlado en ledger-service: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status",    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error",     "INTERNAL_SERVER_ERROR",
                "message",   "Error interno del servidor. Contacte al equipo de soporte."
        ));
    }
}