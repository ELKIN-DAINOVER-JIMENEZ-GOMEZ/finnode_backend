package com.finnode.ledger.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta del saldo calculado de una cuenta por Event Sourcing.
 *
 * Es la respuesta del endpoint:
 *   GET /ledger/{accountId}/balance
 *   GET /ledger/{accountId}/balance?asOf=2025-06-15T00:00:00Z
 *
 * El saldo NO se lee de una columna en base de datos.
 * Se calcula en tiempo real por BalanceCalculatorService sumando
 * todos los asientos históricos de la cuenta:
 *
 *   computedBalance =
 *     Σ amount WHERE entry_type IN (CREDIT, REVERSAL_CREDIT)
 *     - Σ amount WHERE entry_type IN (DEBIT, REVERSAL_DEBIT)
 *
 * Esto garantiza que el saldo aquí siempre sea consistente con el
 * historial de asientos — no puede haber desincronía entre ambos.
 */

@Builder
public record BalanceResponse(

        /**
         * Cuenta cuyo saldo fue calculado.
         */
        UUID accountId,

        /**
         * Saldo calculado por Event Sourcing.
         * Si se pasó el parámetro asOf, es el saldo en ese momento histórico.
         * Si no se pasó, es el saldo actual (todos los asientos hasta ahora).
         */
        BigDecimal computedBalance,

        /**
         * Total de asientos contabilizados para calcular este saldo.
         * Provee contexto al número: "saldo calculado sobre 47 movimientos".
         * También sirve como señal de alerta si es 0 en una cuenta activa.
         */
        long totalEntries,

        /**
         * Timestamp exacto en que se realizó el cálculo.
         * Si se pasó ?asOf=..., este campo refleja ese valor.
         * Si no, refleja el Instant.now() del momento de la consulta.
         *
         * Permite al frontend mostrar: "Saldo al 15 de junio 2025, 10:31 AM"
         */
        Instant asOf

) {}