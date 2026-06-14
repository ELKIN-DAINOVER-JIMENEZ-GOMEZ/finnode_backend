package com.finnode.ledger.controller;

import com.finnode.ledger.dto.BalanceResponse;
import com.finnode.ledger.dto.LedgerEntryResponse;
import com.finnode.ledger.dto.LedgerHistoryResponse;
import com.finnode.ledger.service.BalanceCalculatorService;
import com.finnode.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Controlador REST del ledger-service.
 *
 * Expone ÚNICAMENTE endpoints de lectura (GET).
 * La escritura de asientos ocurre exclusivamente via eventos Kafka —
 * no existe ningún endpoint POST, PUT, PATCH o DELETE en este servicio.
 *
 * SEGURIDAD A NIVEL DE CONTROLADOR:
 * Aunque SecurityConfig ya exige JWT válido para toda petición, aquí
 * se añade una segunda validación: el usuario extraído del JWT debe ser
 * el propietario de la cuenta solicitada. Un usuario autenticado no puede
 * ver el historial contable de otro usuario.
 *
 * Esta verificación de ownership se hace comparando el "sub" (subject)
 * del JWT con el accountId de la URL. En FinNode, el auth-service incluye
 * el accountId del usuario como claim "accountId" dentro del JWT al momento
 * del login, lo que permite esta validación sin consultar otra base de datos.
 *
 * CQRS — QUERY SIDE:
 * Este controlador representa el lado de lectura del patrón CQRS.
 * No modifica estado — solo consulta el libro mayor y retorna datos.
 */
@Slf4j
@RestController
@RequestMapping("/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService           ledgerService;
    private final BalanceCalculatorService balanceCalculatorService;

    // -------------------------------------------------------------------------
    // HISTORIAL DE ASIENTOS
    // -------------------------------------------------------------------------

    /**
     * Retorna el historial contable completo de una cuenta.
     * Incluye todos los asientos ordenados del más reciente al más antiguo,
     * junto con los totales de débitos, créditos y balance neto del período.
     *
     * Requiere: JWT válido + ser el propietario de la cuenta.
     *
     * Ejemplo de request:
     *   GET /ledger/acc-1a2b3c4d-.../entries
     *   Authorization: Bearer eyJhbGc...
     *
     * Ejemplo de respuesta exitosa (HTTP 200):
     * {
     *   "accountId":    "acc-1a2b3c4d-...",
     *   "entries":      [...],
     *   "totalDebits":  1500000.00,
     *   "totalCredits": 2000000.00,
     *   "netBalance":   500000.00,
     *   "periodFrom":   "2025-01-01T00:00:00Z",
     *   "periodTo":     "2025-01-15T10:31:12Z",
     *   "totalEntries": 4
     * }
     *
     * @param accountId ID de la cuenta a consultar (extraído de la URL)
     * @param jwt       token JWT del usuario autenticado (inyectado por Spring Security)
     * @return historial completo con totales calculados
     */
    @GetMapping("/{accountId}/entries")
    public ResponseEntity<LedgerHistoryResponse> getHistory(
            @PathVariable UUID accountId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("[CONTROLLER] GET /ledger/{}/entries | usuario={}",
                accountId, jwt.getSubject());

        validateOwnership(accountId, jwt);

        LedgerHistoryResponse history = ledgerService.getHistory(accountId);

        return ResponseEntity.ok(history);
    }

    /**
     * Retorna el historial contable de una cuenta filtrado por rango de fechas.
     * Útil para generar extractos mensuales o consultar movimientos de un período.
     *
     * Requiere: JWT válido + ser el propietario de la cuenta.
     *
     * Ejemplo de request:
     *   GET /ledger/acc-1a2b3c4d-.../entries?from=2025-01-01T00:00:00Z&to=2025-01-31T23:59:59Z
     *   Authorization: Bearer eyJhbGc...
     *
     * @param accountId ID de la cuenta a consultar
     * @param from      inicio del período en formato ISO-8601 (inclusive)
     * @param to        fin del período en formato ISO-8601 (inclusive)
     * @param jwt       token JWT del usuario autenticado
     * @return historial filtrado con totales del período indicado
     */
    @GetMapping(value = "/{accountId}/entries", params = {"from", "to"})
    public ResponseEntity<LedgerHistoryResponse> getHistoryByDateRange(
            @PathVariable UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("[CONTROLLER] GET /ledger/{}/entries?from={}&to={} | usuario={}",
                accountId, from, to, jwt.getSubject());

        validateOwnership(accountId, jwt);
        validateDateRange(from, to);

        LedgerHistoryResponse history = ledgerService.getHistoryByDateRange(accountId, from, to);

        return ResponseEntity.ok(history);
    }

    // -------------------------------------------------------------------------
    // SALDO POR EVENT SOURCING
    // -------------------------------------------------------------------------

    /**
     * Calcula y retorna el saldo actual de una cuenta por Event Sourcing.
     * El saldo no se lee de una columna — se calcula sumando todos los
     * asientos históricos de la cuenta en tiempo real.
     *
     * Si se pasa el parámetro opcional "asOf", retorna el saldo histórico
     * de la cuenta en ese momento específico del tiempo.
     *
     * Requiere: JWT válido + ser el propietario de la cuenta.
     *
     * Ejemplo de request (saldo actual):
     *   GET /ledger/acc-1a2b3c4d-.../balance
     *   Authorization: Bearer eyJhbGc...
     *
     * Ejemplo de request (saldo histórico):
     *   GET /ledger/acc-1a2b3c4d-.../balance?asOf=2025-06-15T10:31:00Z
     *   Authorization: Bearer eyJhbGc...
     *
     * Ejemplo de respuesta exitosa (HTTP 200):
     * {
     *   "accountId":       "acc-1a2b3c4d-...",
     *   "computedBalance": 500000.00,
     *   "totalEntries":    47,
     *   "asOf":            "2025-01-15T10:31:12Z"
     * }
     *
     * @param accountId ID de la cuenta
     * @param asOf      timestamp opcional para cálculo histórico (ISO-8601)
     * @param jwt       token JWT del usuario autenticado
     * @return saldo calculado por Event Sourcing
     */
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf,
            @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("[CONTROLLER] GET /ledger/{}/balance | asOf={} | usuario={}",
                accountId, asOf, jwt.getSubject());

        validateOwnership(accountId, jwt);

        BalanceResponse balance = (asOf == null)
                ? balanceCalculatorService.calculateCurrentBalance(accountId)
                : balanceCalculatorService.calculateBalanceAsOf(accountId, asOf);

        return ResponseEntity.ok(balance);
    }

    // -------------------------------------------------------------------------
    // CONSULTA POR TRANSACCIÓN
    // -------------------------------------------------------------------------

    /**
     * Retorna todos los asientos asociados a una transacción específica.
     *
     * En una transferencia exitosa retorna 2 asientos (DEBIT + CREDIT).
     * En una transacción revertida retorna 4 asientos
     * (DEBIT + CREDIT + REVERSAL_DEBIT + REVERSAL_CREDIT).
     *
     * Útil para que el usuario consulte el detalle completo de una
     * transferencia específica, incluyendo si fue revertida.
     *
     * Requiere: JWT válido.
     * NOTA: este endpoint no valida ownership porque el transactionId
     * no está directamente asociado a una cuenta en la URL. La validación
     * se delega a LedgerService — solo retorna asientos si la transacción
     * existe en el libro mayor.
     *
     * Ejemplo de request:
     *   GET /ledger/transactions/txn-9f1e2a3b-...
     *   Authorization: Bearer eyJhbGc...
     *
     * @param transactionId ID de la transacción a consultar
     * @param jwt           token JWT del usuario autenticado
     * @return lista de asientos de la transacción
     */
    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<List<LedgerEntryResponse>> getEntriesByTransaction(
            @PathVariable UUID transactionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("[CONTROLLER] GET /ledger/transactions/{} | usuario={}",
                transactionId, jwt.getSubject());

        List<LedgerEntryResponse> entries = ledgerService
                .
                getEntriesByTransactionId(transactionId);

        return ResponseEntity.ok(entries);
    }

    // -------------------------------------------------------------------------
    // MÉTODOS PRIVADOS DE VALIDACIÓN
    // -------------------------------------------------------------------------

    /**
     * Valida que el usuario autenticado sea el propietario de la cuenta solicitada.
     *
     * El auth-service incluye el claim "accountId" en el JWT al momento del login.
     * Este claim contiene el UUID de la cuenta bancaria del usuario autenticado.
     * Si el accountId del JWT no coincide con el accountId de la URL, se lanza
     * una excepción de acceso denegado → HTTP 403.
     *
     * Esto previene que un usuario autenticado consulte el historial contable
     * de otro usuario simplemente cambiando el UUID en la URL.
     *
     * @param accountId UUID de la cuenta solicitada en la URL
     * @param jwt       token JWT del usuario autenticado
     * @throws org.springframework.security.access.AccessDeniedException si no coinciden
     */
    private void validateOwnership(UUID accountId, Jwt jwt) {
        String accountIdFromToken = jwt.getClaimAsString("accountId");

        if (accountIdFromToken == null || !accountIdFromToken.equals(accountId.toString())) {
            log.warn("[SECURITY] Intento de acceso no autorizado | accountId solicitado={} | accountId en token={}",
                    accountId, accountIdFromToken);
            throw new org.springframework.security.access.AccessDeniedException(
                    "No tienes permiso para consultar esta cuenta"
            );
        }
    }

    /**
     * Valida que el rango de fechas sea coherente.
     * "from" debe ser anterior a "to" — un rango invertido no tiene sentido
     * y retornaría resultados vacíos de forma confusa para el cliente.
     *
     * @param from inicio del período
     * @param to   fin del período
     * @throws IllegalArgumentException si "from" es posterior a "to"
     */
    private void validateDateRange(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "El parámetro 'from' debe ser anterior a 'to' | from=" + from + " | to=" + to
            );
        }
    }
}