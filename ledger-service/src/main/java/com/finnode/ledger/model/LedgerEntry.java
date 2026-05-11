package com.finnode.ledger.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Asiento contable inmutable.
 *
 * Representa un movimiento financiero individual dentro del libro mayor.
 * Por cada transacción se crean exactamente DOS registros de esta entidad:
 * un DEBIT (salida de la cuenta origen) y un CREDIT (entrada a la cuenta destino).
 *
 * REGLA DE ORO: Esta entidad NUNCA se modifica después de ser creada.
 * No existe updatedAt ni @Version. Si un asiento es incorrecto, se crea
 * un nuevo asiento de compensación (REVERSAL_DEBIT / REVERSAL_CREDIT).
 * Este principio garantiza la trazabilidad completa de auditoría.
 */
@Entity
@Table(
        name = "ledger_entries",
        indexes = {
                @Index(name = "idx_ledger_account_id",       columnList = "account_id"),
                @Index(name = "idx_ledger_transaction_id",   columnList = "transaction_id"),
                @Index(name = "idx_ledger_account_created",  columnList = "account_id, created_at DESC")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    /**
     * Identificador único del asiento contable.
     * Generado automáticamente por la base de datos.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * ID de la transacción que originó este asiento.
     * Agrupa el par DEBIT/CREDIT de una misma operación.
     * Publicado por el payment-orchestrator en el evento PaymentCompletedEvent.
     */
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    /**
     * Cuenta a la que pertenece este asiento.
     * Es una FK lógica: no hay un JOIN a otra tabla porque
     * account-service tiene su propia base de datos aislada.
     */
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    /**
     * La otra cuenta involucrada en la transferencia.
     * Si este asiento es un DEBIT de Juan, counterpartAccountId es la cuenta de María.
     * Facilita la reconstrucción del historial de transferencias entre cuentas.
     */
    @Column(name = "counterpart_account_id", nullable = false, updatable = false)
    private UUID counterpartAccountId;

    /**
     * Tipo de asiento contable.
     * Define si este asiento representa una salida (DEBIT), entrada (CREDIT)
     * o la reversión de alguno de los dos (REVERSAL_DEBIT, REVERSAL_CREDIT).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 20)
    private EntryType entryType;

    /**
     * Monto del asiento. Siempre positivo.
     * La dirección del movimiento la determina el entryType, no el signo.
     * Precisión de 19 dígitos con 4 decimales para cumplir ISO 4217.
     */
    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * Código de moneda ISO 4217 (COP, USD, EUR).
     * Incluido en cada asiento para soportar cuentas multi-moneda en el futuro.
     */
    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    /**
     * Descripción legible del movimiento para interfaces de usuario y auditoría.
     * Ejemplo: "Transferencia enviada a María García" / "Transferencia recibida de Juan Pérez"
     */
    @Column(name = "description", updatable = false, length = 255)
    private String description;

    /**
     * Timestamp exacto en que fue creado el asiento.
     * Gestionado automáticamente por Hibernate. NUNCA se modifica.
     * Es la base del Event Sourcing: permite recalcular el saldo en cualquier punto del tiempo.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /*
     * -----------------------------------------------------------------------
     * AUSENCIA INTENCIONAL DE:
     *   - updatedAt       → los asientos nunca se modifican
     *   - @Version        → el optimistic locking no aplica a registros inmutables
     *   - setters         → Lombok @Getter sin @Setter para reforzar inmutabilidad
     * -----------------------------------------------------------------------
     */
}