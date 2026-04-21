package com.finnode.account.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de salida simplificado para consultas de saldo.
 * Usado en: GET /accounts/{accountId}/balance
 *
 * Más liviano que AccountResponse — el frontend del dashboard lo usa
 * para mostrar el saldo en tiempo real sin necesidad de todos los metadatos.
 */
@Builder
public record BalanceResponse(

        /** ID de la cuenta consultada. */
        UUID accountId,

        /**
         * Saldo disponible para operar: balance - reservedBalance.
         * Es el único valor relevante para el usuario en una consulta de saldo.
         */
        BigDecimal availableBalance,

        /** Monto actualmente bloqueado en transferencias en tránsito. */
        BigDecimal reservedBalance,

        /** Moneda de la cuenta. Por ahora siempre "COP". */
        String currency
) {
    /**
     * Factory method para construir el DTO desde la entidad Account.
     *
     * @param account entidad JPA persistida
     * @return DTO listo para serializar a JSON
     */
    public static BalanceResponse from(com.finnode.account.model.Account account) {
        return BalanceResponse.builder()
                .accountId(account.getId())
                .availableBalance(account.getAvailableBalance())
                .reservedBalance(account.getReservedBalance())
                .currency("COP")
                .build();
    }
}