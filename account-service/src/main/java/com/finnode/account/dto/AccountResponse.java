package com.finnode.account.dto;

import com.finnode.account.model.AccountStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida con el detalle completo de una cuenta bancaria.
 * Usado en: GET /accounts/{accountId}
 *
 * Nunca expone la entidad Account directamente — este DTO es el contrato
 * público de la API y desacopla la estructura interna del modelo JPA.
 */
@Builder
public record AccountResponse(

        /** ID único de la cuenta bancaria. */
        UUID accountId,

        /** ID del usuario propietario (referencia lógica al auth-service). */
        UUID userId,

        /** Número de cuenta visible al usuario (ej: "FN-0000000001"). */
        String accountNumber,

        /** Saldo total real de la cuenta (incluye montos en reserva). */
        BigDecimal balance,

        /** Monto bloqueado en transferencias aún no confirmadas. */
        BigDecimal reservedBalance,

        /**
         * Saldo disponible para operar: balance - reservedBalance.
         * Este es el único valor que el frontend debe mostrar al usuario.
         */
        BigDecimal availableBalance,

        /** Estado actual de la cuenta: ACTIVE, SUSPENDED o CLOSED. */
        AccountStatus status,

        /** Fecha de creación de la cuenta. */
        LocalDateTime createdAt
) {
    /**
     * Factory method para construir el DTO desde la entidad Account.
     * Centraliza el mapeo en un solo lugar para evitar duplicación.
     *
     * @param account entidad JPA persistida
     * @return DTO listo para serializar a JSON
     */
    public static AccountResponse from(com.finnode.account.model.Account account) {
        return AccountResponse.builder()
                .accountId(account.getId())
                .userId(account.getUserId())
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .reservedBalance(account.getReservedBalance())
                .availableBalance(account.getAvailableBalance())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}