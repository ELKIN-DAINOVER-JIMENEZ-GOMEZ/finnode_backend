package com.finnode.ledger.repository;

import com.finnode.ledger.model.EntryType;
import com.finnode.ledger.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio JPA para los asientos contables del libro mayor.
 *
 * Solo expone operaciones de LECTURA y CREACIÓN.
 * No existe ningún método de UPDATE ni DELETE — los asientos son inmutables
 * por principio contable. Si se heredara un método como save() para
 * actualizar, Spring Data lo permitiría técnicamente, pero la entidad
 * LedgerEntry no tiene setters, lo que lo hace imposible en la práctica.
 *
 * La mayoría de consultas están orientadas a dos casos de uso:
 *   1. Historial de movimientos de una cuenta (para el usuario y auditoría)
 *   2. Cálculo de saldo por Event Sourcing (suma algebraica de asientos)
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    // -------------------------------------------------------------------------
    // CONSULTAS POR CUENTA
    // -------------------------------------------------------------------------

    /**
     * Retorna todos los asientos de una cuenta ordenados del más reciente al más antiguo.
     * Usado por LedgerController para el historial completo.
     *
     * @param accountId ID de la cuenta a consultar
     * @return lista de asientos ordenados por fecha descendente
     */
    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    /**
     * Retorna los asientos de una cuenta dentro de un rango de fechas.
     * Permite filtrar el historial por período (ej: movimientos del mes de enero).
     * Usado por LedgerController cuando se pasan los parámetros ?from= y ?to=
     *
     * @param accountId ID de la cuenta
     * @param from      inicio del período (inclusive)
     * @param to        fin del período (inclusive)
     * @return lista de asientos en el rango, ordenados por fecha descendente
     */
    List<LedgerEntry> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID accountId,
            Instant from,
            Instant to
    );

    /**
     * Verifica si existe al menos un asiento para la cuenta indicada.
     * Usado en LedgerService para lanzar LedgerEntryNotFoundException antes
     * de intentar calcular el saldo de una cuenta sin movimientos.
     *
     * @param accountId ID de la cuenta
     * @return true si la cuenta tiene al menos un asiento registrado
     */
    boolean existsByAccountId(UUID accountId);

    // -------------------------------------------------------------------------
    // CONSULTAS POR TRANSACCIÓN
    // -------------------------------------------------------------------------

    /**
     * Retorna todos los asientos asociados a una transacción específica.
     * En una transferencia normal retorna 2 asientos (DEBIT + CREDIT).
     * En una transacción revertida retorna 4 asientos (DEBIT + CREDIT + REVERSAL_DEBIT + REVERSAL_CREDIT).
     *
     * @param transactionId ID de la transacción del payment-orchestrator
     * @return lista de asientos de la transacción
     */
    List<LedgerEntry> findByTransactionId(UUID transactionId);

    /**
     * Verifica si ya existen asientos registrados para una transacción.
     * Usado en LedgerService para garantizar idempotencia: si Kafka reentrega
     * un evento ya procesado, no se crean asientos duplicados.
     *
     * @param transactionId ID de la transacción a verificar
     * @return true si la transacción ya fue registrada en el libro mayor
     */
    boolean existsByTransactionId(UUID transactionId);

    // -------------------------------------------------------------------------
    // CONSULTAS PARA EVENT SOURCING (CÁLCULO DE SALDO)
    // -------------------------------------------------------------------------

    /**
     * Suma todos los montos de los asientos de una cuenta filtrados por tipo.
     * Es la consulta central del Event Sourcing en BalanceCalculatorService.
     *
     * El saldo actual se calcula como:
     *   Σ amount WHERE entry_type IN (CREDIT, REVERSAL_CREDIT)   → suma positiva
     *   - Σ amount WHERE entry_type IN (DEBIT,  REVERSAL_DEBIT)  → suma negativa
     *
     * Se usa COALESCE para retornar 0 cuando no hay asientos del tipo indicado,
     * evitando NullPointerException al calcular el primer saldo.
     *
     * @param accountId ID de la cuenta
     * @param entryType tipo de asiento a sumar (DEBIT, CREDIT, REVERSAL_DEBIT, REVERSAL_CREDIT)
     * @return suma total de los montos. Nunca retorna null — mínimo retorna BigDecimal.ZERO
     */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
            FROM LedgerEntry e
            WHERE e.accountId = :accountId
              AND e.entryType = :entryType
            """)
    BigDecimal sumAmountByAccountIdAndEntryType(
            @Param("accountId") UUID accountId,
            @Param("entryType") EntryType entryType
    );

    /**
     * Suma los montos de una cuenta filtrados por tipo Y anteriores a una fecha.
     * Permite calcular el saldo histórico de una cuenta en un punto específico del tiempo.
     *
     * Usado por BalanceCalculatorService cuando se llama:
     *   GET /ledger/{accountId}/balance?asOf=2025-06-15T00:00:00Z
     *
     * @param accountId ID de la cuenta
     * @param entryType tipo de asiento a sumar
     * @param asOf      fecha límite: solo se suman asientos creados ANTES de esta fecha
     * @return suma total hasta la fecha indicada
     */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
            FROM LedgerEntry e
            WHERE e.accountId  = :accountId
              AND e.entryType  = :entryType
              AND e.createdAt <= :asOf
            """)
    BigDecimal sumAmountByAccountIdAndEntryTypeAsOf(
            @Param("accountId") UUID accountId,
            @Param("entryType") EntryType entryType,
            @Param("asOf")      Instant asOf
    );

    /**
     * Cuenta el total de asientos registrados para una cuenta.
     * Incluido en BalanceResponse para dar contexto al saldo calculado
     * (ej: "saldo calculado sobre 47 movimientos").
     *
     * @param accountId ID de la cuenta
     * @return número total de asientos
     */
    long countByAccountId(UUID accountId);
}