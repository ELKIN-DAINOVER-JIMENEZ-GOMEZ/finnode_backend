package com.finnode.ledger.dto;

import com.finnode.ledger.model.EntryType;
import com.finnode.ledger.model.LedgerEntry;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta que representa un único asiento contable.
 *
 * Es la unidad mínima de información que este servicio expone al exterior.
 * Se construye a partir de una entidad LedgerEntry usando el método
 * estático fromEntity(), manteniendo la capa de persistencia aislada
 * de la capa de presentación.
 *
 * Usado como elemento de la lista dentro de LedgerHistoryResponse.
 */
@Builder
public record LedgerEntryResponse(

        /**
         * Identificador único del asiento contable.
         */
        UUID entryId,

        /**
         * ID de la transacción que originó este asiento.
         * Permite agrupar el par DEBIT/CREDIT de una misma operación.
         */
        UUID transactionId,

        /**
         * Cuenta a la que pertenece este asiento.
         */
        UUID accountId,

        /**
         * La otra cuenta involucrada en la transferencia.
         */
        UUID counterpartAccountId,

        /**
         * Tipo de asiento: DEBIT, CREDIT, REVERSAL_DEBIT o REVERSAL_CREDIT.
         */
        EntryType entryType,

        /**
         * Monto del asiento. Siempre positivo.
         * La dirección del movimiento la indica el entryType.
         */
        BigDecimal amount,

        /**
         * Código de moneda ISO 4217 (COP, USD, EUR).
         */
        String currency,

        /**
         * Descripción legible del movimiento.
         * Ejemplo: "Transferencia enviada a María García"
         */
        String description,

        /**
         * Timestamp exacto de creación del asiento.
         * Inmutable — refleja cuándo ocurrió el movimiento financiero.
         */
        Instant createdAt

) {
    /**
     * Convierte una entidad JPA en su representación de respuesta.
     * Centraliza el mapeo para no repetirlo en cada servicio o controlador.
     *
     * @param entry entidad LedgerEntry desde la base de datos
     * @return DTO listo para serializar a JSON
     */
    public static LedgerEntryResponse fromEntity(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
                .entryId(entry.getId())
                .transactionId(entry.getTransactionId())
                .accountId(entry.getAccountId())
                .counterpartAccountId(entry.getCounterpartAccountId())
                .entryType(entry.getEntryType())
                .amount(entry.getAmount())
                .currency(entry.getCurrency())
                .description(entry.getDescription())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}