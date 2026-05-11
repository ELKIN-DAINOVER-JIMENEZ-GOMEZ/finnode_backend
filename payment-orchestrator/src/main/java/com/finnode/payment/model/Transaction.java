package com.finnode.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_transactions_transaction_id", columnList = "transaction_id", unique = true),
                @Index(name = "idx_transactions_user_id",        columnList = "user_id"),
                @Index(name = "idx_transactions_status",         columnList = "status"),
                @Index(name = "idx_transactions_user_created",   columnList = "user_id, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    /** Identificador interno de la entidad — nunca se expone en la API. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Identificador público de la transacción.
     * Se comparte con account-service y ledger-service como clave de correlación
     * para rastrear el mismo pago a través de todos los microservicios.
     */
    @Column(name = "transaction_id", nullable = false, unique = true, updatable = false)
    private UUID transactionId;

    /** Referencia lógica al usuario que inicia el pago (proviene del JWT). */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Cuenta bancaria que será debitada. */
    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    /** Cuenta bancaria que será acreditada. */
    @Column(name = "destination_account_id", nullable = false, updatable = false)
    private UUID destinationAccountId;

    /** Monto de la transferencia — siempre positivo. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Código de moneda ISO 4217 (ej: COP, USD, EUR). */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * Estado actual de la transacción en el flujo del Saga.
     * Transiciones válidas:
     *   PENDING → FRAUD_REJECTED (terminal)
     *   PENDING → FUNDS_RESERVED → LEDGER_RECORDED → COMPLETED (terminal exitoso)
     *   PENDING → FAILED (terminal, sin fondos reservados)
     *   FUNDS_RESERVED → REVERSED (terminal, con compensación)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    /**
     * Paso actual dentro del Saga.
     * Permite diagnosticar exactamente en qué punto del flujo
     * se encuentra o se detuvo una transacción.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 30)
    private SagaStep currentStep;

    /**
     * Score de riesgo devuelto por el motor de Spring AI (rango: 0.0 – 1.0).
     * Se persiste siempre, incluso para pagos aprobados, para análisis histórico.
     * Null si la evaluación de fraude no pudo completarse.
     */
    @Column(name = "fraud_risk_score")
    private Double fraudRiskScore;

    /**
     * Motivo del fallo si el Saga no llegó a COMPLETED.
     * Ejemplos: "INSUFFICIENT_FUNDS", "LEDGER_RECORDING_TIMEOUT", "FRAUD_DETECTED".
     */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Descripción opcional ingresada por el usuario al iniciar la transferencia. */
    @Column(name = "description", length = 255)
    private String description;

    /** Timestamp de creación — inmutable, se asigna una sola vez al persistir. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp de la última transición de estado — actualizado automáticamente por Hibernate. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}