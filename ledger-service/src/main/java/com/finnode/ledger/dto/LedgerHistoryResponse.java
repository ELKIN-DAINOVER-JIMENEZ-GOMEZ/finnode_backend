package com.finnode.ledger.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta del historial contable completo de una cuenta.
 *
 * Agrupa la lista de asientos junto con los totales calculados del período.
 * Es la respuesta de los endpoints:
 *   GET /ledger/{accountId}/entries
 *   GET /ledger/{accountId}/entries?from=&to=
 *
 * Los totales (totalDebits, totalCredits, netBalance) se calculan en
 * LedgerService a partir de los asientos de la lista — no se consultan
 * por separado a la base de datos, optimizando el número de queries.
 */
@Builder
public record LedgerHistoryResponse(

        /**
         * Cuenta cuyo historial se está consultando.
         */
        UUID accountId,

        /**
         * Lista de asientos contables ordenados del más reciente al más antiguo.
         * Incluye todos los tipos: DEBIT, CREDIT, REVERSAL_DEBIT, REVERSAL_CREDIT.
         */
        List<LedgerEntryResponse> entries,

        /**
         * Suma de todos los asientos DEBIT + REVERSAL_DEBIT del período.
         * Representa el total de fondos que salieron de la cuenta.
         */
        BigDecimal totalDebits,

        /**
         * Suma de todos los asientos CREDIT + REVERSAL_CREDIT del período.
         * Representa el total de fondos que entraron a la cuenta.
         */
        BigDecimal totalCredits,

        /**
         * Balance neto del período: totalCredits - totalDebits.
         * Puede ser negativo si la cuenta envió más de lo que recibió en el período.
         *
         * IMPORTANTE: este no es el saldo actual de la cuenta.
         * Es el movimiento neto dentro del rango de fechas consultado.
         * Para el saldo real, usar GET /ledger/{accountId}/balance.
         */
        BigDecimal netBalance,

        /**
         * Inicio del período consultado.
         * Si no se pasó filtro de fechas, refleja el createdAt del asiento más antiguo.
         */
        Instant periodFrom,

        /**
         * Fin del período consultado.
         * Si no se pasó filtro de fechas, refleja el createdAt del asiento más reciente.
         */
        Instant periodTo,

        /**
         * Total de asientos en la respuesta.
         * Útil para paginación futura y para que el frontend sepa cuántos
         * movimientos tuvo la cuenta en el período sin contar el array.
         */
        int totalEntries

) {}