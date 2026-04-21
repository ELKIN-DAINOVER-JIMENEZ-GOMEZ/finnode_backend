package com.finnode.account.model;



import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidad principal del account-service.
 * <p>
 * Usa dos campos de saldo:
 *   - balance          -> saldo total real de la cuenta
 *   - reservedBalance  -> monto bloqueado en transferencias en transito
 * <p>
 * El saldo disponible para el usuario es: balance - reservedBalance.
 * {@code @Version} habilita optimistic locking de Hibernate para evitar
 * condiciones de carrera cuando multiples transacciones modifican la misma cuenta.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Referencia lógica al usuario propietario.
     * No es FK real — cada microservicio tiene su propia BD aislada.
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    /**
     * Saldo total actual. DECIMAL(19,4) garantiza precisión financiera.
     */
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * Monto bloqueado en transferencias aún no confirmadas.
     * Activa:   balance estable, reservedBalance sube.
     * Confirma: balance baja, reservedBalance baja.
     * Revierte: reservedBalance baja, balance estable.
     */
    @Column(name = "reserved_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal reservedBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    /**
     * Control de optimistic locking.
     * Hibernate incrementa este valor en cada UPDATE.
     * Si dos hilos leen la misma version, el segundo lanza OptimisticLockException.
     * <p>
     *   Hilo A: version=5 -> UPDATE -> version=6
     *   Hilo B: version=5 -> WHERE version=5 -> fila no encontrada -> reintento
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // -------------------------------------------------------------------------
    // Métodos de negocio
    // -------------------------------------------------------------------------

    /** Saldo disponible real para el usuario. El único valor que mostrar en UI. */
    public BigDecimal getAvailableBalance() {
        return balance.subtract(reservedBalance);
    }

    /** Verifica si hay fondos suficientes para una operación. */
    public boolean hasSufficientFunds(BigDecimal amount) {
        return getAvailableBalance().compareTo(amount) >= 0;
    }

    /** Reserva fondos: sube reservedBalance, balance no cambia. */
    public void reserveFunds(BigDecimal amount) {
        this.reservedBalance = this.reservedBalance.add(amount);
    }

    /** Confirma débito definitivo: baja balance y libera la reserva. */
    public void confirmDebit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
        this.reservedBalance = this.reservedBalance.subtract(amount);
    }

    /** Acredita fondos recibidos de una transferencia exitosa. */
    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    /** Libera reserva (rollback Saga): baja reservedBalance, balance no cambia. */
    public void releaseReserve(BigDecimal amount) {
        this.reservedBalance = this.reservedBalance.subtract(amount);
    }

    /** Verifica si la cuenta está operativa. */
    public boolean isActive() {
        return AccountStatus.ACTIVE.equals(this.status);
    }
}