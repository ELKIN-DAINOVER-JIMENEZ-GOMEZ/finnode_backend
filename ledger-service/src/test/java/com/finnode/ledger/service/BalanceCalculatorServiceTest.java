package com.finnode.ledger.service;

import com.finnode.ledger.dto.BalanceResponse;
import com.finnode.ledger.exception.LedgerEntryNotFoundException;
import com.finnode.ledger.model.EntryType;
import com.finnode.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para BalanceCalculatorService.
 *
 * Prueba la lógica de Event Sourcing para el cálculo de saldo:
 * - Cálculo del saldo actual sumando todos los asientos históricos
 * - Cálculo del saldo histórico en un punto específico del tiempo
 * - Manejo de los cuatro tipos de asientos (DEBIT, CREDIT, REVERSAL_DEBIT, REVERSAL_CREDIT)
 * - Validación de que la cuenta existe
 */
@ExtendWith(MockitoExtension.class)
class BalanceCalculatorServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private BalanceCalculatorService balanceCalculatorService;

    private UUID accountId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
    }

    // =========================================================================
    // calculateCurrentBalance: Saldo actual
    // =========================================================================

    @Test
    void calculateCurrentBalance_shouldCalculateSaldoActual() {
        // Given
        BigDecimal credits = new BigDecimal("2000.00");
        BigDecimal reversalCredits = new BigDecimal("500.00");
        BigDecimal debits = new BigDecimal("1000.00");
        BigDecimal reversalDebits = new BigDecimal("200.00");

        when(ledgerEntryRepository.existsByAccountId(accountId)).thenReturn(true);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.CREDIT))
                .thenReturn(credits);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.REVERSAL_CREDIT))
                .thenReturn(reversalCredits);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.DEBIT))
                .thenReturn(debits);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.REVERSAL_DEBIT))
                .thenReturn(reversalDebits);
        when(ledgerEntryRepository.countByAccountId(accountId)).thenReturn(10L);

        // When
        BalanceResponse response = balanceCalculatorService.calculateCurrentBalance(accountId);

        // Then
        // Fórmula: (CREDIT + REVERSAL_CREDIT) - (DEBIT + REVERSAL_DEBIT)
        // = (2000 + 500) - (1000 + 200)
        // = 2500 - 1200
        // = 1300
        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.computedBalance()).isEqualByComparingTo(new BigDecimal("1300.00"));
        assertThat(response.totalEntries()).isEqualTo(10L);
        assertThat(response.asOf()).isNotNull();
    }

    @Test
    void calculateCurrentBalance_shouldThrowExceptionForNonExistentAccount() {
        // Given
        when(ledgerEntryRepository.existsByAccountId(accountId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> balanceCalculatorService.calculateCurrentBalance(accountId))
                .isInstanceOf(LedgerEntryNotFoundException.class);
    }

    @Test
    void calculateCurrentBalance_shouldHandleZeroBalance() {
        // Given
        BigDecimal amount = new BigDecimal("1000.00");

        when(ledgerEntryRepository.existsByAccountId(accountId)).thenReturn(true);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.CREDIT))
                .thenReturn(amount);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.REVERSAL_CREDIT))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.DEBIT))
                .thenReturn(amount);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.REVERSAL_DEBIT))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.countByAccountId(accountId)).thenReturn(2L);

        // When
        BalanceResponse response = balanceCalculatorService.calculateCurrentBalance(accountId);

        // Then
        assertThat(response.computedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateCurrentBalance_shouldHandleNegativeBalance() {
        // Given
        BigDecimal credits = new BigDecimal("500.00");
        BigDecimal debits = new BigDecimal("1000.00");

        when(ledgerEntryRepository.existsByAccountId(accountId)).thenReturn(true);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.CREDIT))
                .thenReturn(credits);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.REVERSAL_CREDIT))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.DEBIT))
                .thenReturn(debits);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.REVERSAL_DEBIT))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.countByAccountId(accountId)).thenReturn(2L);

        // When
        BalanceResponse response = balanceCalculatorService.calculateCurrentBalance(accountId);

        // Then
        assertThat(response.computedBalance()).isEqualByComparingTo(new BigDecimal("-500.00"));
    }

    @Test
    void calculateCurrentBalance_shouldHandleReversalCancellation() {
        // Given
        // Transacción: DEBIT 1000 + CREDIT 1000 = neto 0
        BigDecimal debit = new BigDecimal("1000.00");
        BigDecimal credit = new BigDecimal("1000.00");
        BigDecimal reversalDebit = new BigDecimal("1000.00");
        BigDecimal reversalCredit = new BigDecimal("1000.00");

        when(ledgerEntryRepository.existsByAccountId(accountId)).thenReturn(true);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.CREDIT))
                .thenReturn(credit);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.REVERSAL_CREDIT))
                .thenReturn(reversalCredit);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.DEBIT))
                .thenReturn(debit);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryType(accountId, EntryType.REVERSAL_DEBIT))
                .thenReturn(reversalDebit);
        when(ledgerEntryRepository.countByAccountId(accountId)).thenReturn(4L);

        // When
        BalanceResponse response = balanceCalculatorService.calculateCurrentBalance(accountId);

        // Then
        // (1000 + 1000) - (1000 + 1000) = 0
        assertThat(response.computedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // =========================================================================
    // calculateBalanceAsOf: Saldo histórico
    // =========================================================================

    @Test
    void calculateBalanceAsOf_shouldCalculateHistoricalBalance() {
        // Given
        Instant asOf = Instant.parse("2025-06-15T10:31:00Z");
        BigDecimal credits = new BigDecimal("1500.00");
        BigDecimal debits = new BigDecimal("700.00");

        when(ledgerEntryRepository.existsByAccountId(accountId)).thenReturn(true);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.CREDIT, asOf))
                .thenReturn(credits);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.REVERSAL_CREDIT, asOf))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.DEBIT, asOf))
                .thenReturn(debits);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.REVERSAL_DEBIT, asOf))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.countByAccountId(accountId)).thenReturn(5L);

        // When
        BalanceResponse response = balanceCalculatorService.calculateBalanceAsOf(accountId, asOf);

        // Then
        // (1500 + 0) - (700 + 0) = 800
        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.computedBalance()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(response.asOf()).isEqualTo(asOf);
        assertThat(response.totalEntries()).isEqualTo(5L);
    }

    @Test
    void calculateBalanceAsOf_shouldThrowExceptionForNonExistentAccount() {
        // Given
        Instant asOf = Instant.parse("2025-06-15T10:31:00Z");

        when(ledgerEntryRepository.existsByAccountId(accountId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> balanceCalculatorService.calculateBalanceAsOf(accountId, asOf))
                .isInstanceOf(LedgerEntryNotFoundException.class);
    }

    @Test
    void calculateBalanceAsOf_shouldIncludeReversalsInHistoricalBalance() {
        // Given
        Instant asOf = Instant.parse("2025-06-15T10:31:00Z");
        BigDecimal credits = new BigDecimal("2000.00");
        BigDecimal reversalCredits = new BigDecimal("500.00");
        BigDecimal debits = new BigDecimal("1000.00");
        BigDecimal reversalDebits = new BigDecimal("300.00");

        when(ledgerEntryRepository.existsByAccountId(accountId)).thenReturn(true);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.CREDIT, asOf))
                .thenReturn(credits);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.REVERSAL_CREDIT, asOf))
                .thenReturn(reversalCredits);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.DEBIT, asOf))
                .thenReturn(debits);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.REVERSAL_DEBIT, asOf))
                .thenReturn(reversalDebits);
        when(ledgerEntryRepository.countByAccountId(accountId)).thenReturn(8L);

        // When
        BalanceResponse response = balanceCalculatorService.calculateBalanceAsOf(accountId, asOf);

        // Then
        // (2000 + 500) - (1000 + 300) = 1200
        assertThat(response.computedBalance()).isEqualByComparingTo(new BigDecimal("1200.00"));
    }

    @Test
    void calculateBalanceAsOf_shouldHandleNoEntriesBeforeAsOfDate() {
        // Given
        Instant asOf = Instant.parse("2025-06-15T10:31:00Z");

        when(ledgerEntryRepository.existsByAccountId(accountId)).thenReturn(true);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.CREDIT, asOf))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.REVERSAL_CREDIT, asOf))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.DEBIT, asOf))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.REVERSAL_DEBIT, asOf))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.countByAccountId(accountId)).thenReturn(0L);

        // When
        BalanceResponse response = balanceCalculatorService.calculateBalanceAsOf(accountId, asOf);

        // Then
        assertThat(response.computedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateBalanceAsOf_shouldUseCorrectAsOfTimestamp() {
        // Given
        Instant asOf = Instant.parse("2025-01-15T10:31:00Z");

        when(ledgerEntryRepository.existsByAccountId(accountId)).thenReturn(true);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.CREDIT, asOf))
                .thenReturn(new BigDecimal("500.00"));
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.REVERSAL_CREDIT, asOf))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.DEBIT, asOf))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountId, EntryType.REVERSAL_DEBIT, asOf))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.countByAccountId(accountId)).thenReturn(1L);

        // When
        BalanceResponse response = balanceCalculatorService.calculateBalanceAsOf(accountId, asOf);

        // Then
        assertThat(response.asOf()).isEqualTo(asOf);
    }

    @Test
    void calculateBalanceAsOf_auditScenario_SaldoEnMomentoDeTransaccionDisputa() {
        // Scenario: Determinar el saldo exacto al momento de una transacción disputada
        // Given
        Instant disputedTransactionTime = Instant.parse("2025-06-15T10:31:00Z");
        UUID accountBeingAudited = UUID.randomUUID();

        // Datos ficticios: En ese momento, la cuenta tenía estas sumas
        BigDecimal creditsUntilDispute = new BigDecimal("50000.00");   // Ingresos
        BigDecimal debitsUntilDispute = new BigDecimal("30000.00");    // Egresos

        when(ledgerEntryRepository.existsByAccountId(accountBeingAudited)).thenReturn(true);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountBeingAudited, EntryType.CREDIT, disputedTransactionTime))
                .thenReturn(creditsUntilDispute);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountBeingAudited, EntryType.REVERSAL_CREDIT, disputedTransactionTime))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountBeingAudited, EntryType.DEBIT, disputedTransactionTime))
                .thenReturn(debitsUntilDispute);
        when(ledgerEntryRepository.sumAmountByAccountIdAndEntryTypeAsOf(accountBeingAudited, EntryType.REVERSAL_DEBIT, disputedTransactionTime))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.countByAccountId(accountBeingAudited)).thenReturn(100L);

        // When: Auditor consulta el saldo en ese momento específico
        BalanceResponse auditResponse = balanceCalculatorService.calculateBalanceAsOf(accountBeingAudited, disputedTransactionTime);

        // Then: El saldo es exacto para ese punto en el tiempo
        assertThat(auditResponse.computedBalance())
                .isEqualByComparingTo(new BigDecimal("20000.00"));
        assertThat(auditResponse.asOf())
                .isEqualTo(disputedTransactionTime);
    }
}
