package com.finnode.ledger.service;

import com.finnode.ledger.dto.LedgerEntryResponse;
import com.finnode.ledger.dto.LedgerHistoryResponse;
import com.finnode.ledger.event.LedgerEntriesRecordedEvent;
import com.finnode.ledger.event.PaymentCompletedEvent;
import com.finnode.ledger.event.PaymentReversedEvent;
import com.finnode.ledger.exception.LedgerEntryNotFoundException;
import com.finnode.ledger.exception.LedgerImbalanceException;
import com.finnode.ledger.kafka.LedgerEventPublisher;
import com.finnode.ledger.model.EntryType;
import com.finnode.ledger.model.LedgerEntry;
import com.finnode.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para LedgerService.
 *
 * Cubre:
 *   - recordEntries: Registro de asientos DEBIT + CREDIT exitosos
 *   - recordReversalEntries: Registro de asientos de compensación REVERSAL_*
 *   - getHistory: Consulta de historial completo
 *   - getHistoryByDateRange: Consulta de historial filtrado por fechas
 *   - getEntriesByTransactionId: Consulta por ID de transacción
 *   - Validaciones de monto, moneda y balances
 *   - Idempotencia de eventos
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private LedgerEventPublisher ledgerEventPublisher;

    @InjectMocks
    private LedgerService ledgerService;

    private UUID transactionId;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private BigDecimal amount;
    private String currency;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();
        sourceAccountId = UUID.randomUUID();
        destinationAccountId = UUID.randomUUID();
        amount = new BigDecimal("500000.00");
        currency = "COP";
    }

    // =========================================================================
    // recordEntries: Registro de asientos exitosos
    // =========================================================================

    @Test
    void recordEntries_shouldCreateDebitAndCreditEntries() {
        // Given
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                Instant.now()
        );

        LedgerEntry debitEntry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .accountId(sourceAccountId)
                .counterpartAccountId(destinationAccountId)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .currency(currency)
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .accountId(destinationAccountId)
                .counterpartAccountId(sourceAccountId)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .currency(currency)
                .build();

        when(ledgerEntryRepository.existsByTransactionId(transactionId)).thenReturn(false);
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenReturn(debitEntry)
                .thenReturn(creditEntry);

        // When
        ledgerService.recordEntries(event);

        // Then
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
        verify(ledgerEventPublisher, times(1)).publishLedgerEntriesRecorded(any(LedgerEntriesRecordedEvent.class));
    }

    @Test
    void recordEntries_shouldBeIdempotent() {
        // Given
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                Instant.now()
        );

        when(ledgerEntryRepository.existsByTransactionId(transactionId)).thenReturn(true);

        // When
        ledgerService.recordEntries(event);

        // Then
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
        verify(ledgerEventPublisher, never()).publishLedgerEntriesRecorded(any());
    }

    @Test
    void recordEntries_shouldThrowExceptionForNullAmount() {
        // Given
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                null,
                currency,
                Instant.now()
        );

        when(ledgerEntryRepository.existsByTransactionId(transactionId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> ledgerService.recordEntries(event))
                .isInstanceOf(LedgerImbalanceException.class)
                .hasMessageContaining("Monto inválido");
    }

    @Test
    void recordEntries_shouldThrowExceptionForZeroAmount() {
        // Given
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                BigDecimal.ZERO,
                currency,
                Instant.now()
        );

        when(ledgerEntryRepository.existsByTransactionId(transactionId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> ledgerService.recordEntries(event))
                .isInstanceOf(LedgerImbalanceException.class);
    }

    @Test
    void recordEntries_shouldThrowExceptionForNegativeAmount() {
        // Given
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("-100.00"),
                currency,
                Instant.now()
        );

        when(ledgerEntryRepository.existsByTransactionId(transactionId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> ledgerService.recordEntries(event))
                .isInstanceOf(LedgerImbalanceException.class);
    }

    @Test
    void recordEntries_shouldThrowExceptionForNullCurrency() {
        // Given
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                amount,
                null,
                Instant.now()
        );

        when(ledgerEntryRepository.existsByTransactionId(transactionId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> ledgerService.recordEntries(event))
                .isInstanceOf(LedgerImbalanceException.class)
                .hasMessageContaining("Moneda inválida");
    }

    @Test
    void recordEntries_shouldThrowExceptionForBlankCurrency() {
        // Given
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                amount,
                "   ",
                Instant.now()
        );

        when(ledgerEntryRepository.existsByTransactionId(transactionId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> ledgerService.recordEntries(event))
                .isInstanceOf(LedgerImbalanceException.class);
    }

    @Test
    void recordEntries_shouldPublishConfirmationEvent() {
        // Given
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                Instant.now()
        );

        UUID debitId = UUID.randomUUID();
        UUID creditId = UUID.randomUUID();

        LedgerEntry debitEntry = LedgerEntry.builder()
                .id(debitId)
                .transactionId(transactionId)
                .accountId(sourceAccountId)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .currency(currency)
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .id(creditId)
                .transactionId(transactionId)
                .accountId(destinationAccountId)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .currency(currency)
                .build();

        when(ledgerEntryRepository.existsByTransactionId(transactionId)).thenReturn(false);
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenReturn(debitEntry)
                .thenReturn(creditEntry);

        // When
        ledgerService.recordEntries(event);

        // Then
        ArgumentCaptor<LedgerEntriesRecordedEvent> captor = ArgumentCaptor.forClass(LedgerEntriesRecordedEvent.class);
        verify(ledgerEventPublisher).publishLedgerEntriesRecorded(captor.capture());

        LedgerEntriesRecordedEvent publishedEvent = captor.getValue();
        assertThat(publishedEvent.transactionId()).isEqualTo(transactionId);
        assertThat(publishedEvent.debitEntryId()).isEqualTo(debitId);
        assertThat(publishedEvent.creditEntryId()).isEqualTo(creditId);
        assertThat(publishedEvent.reversalConfirmed()).isFalse();
    }

    // =========================================================================
    // recordReversalEntries: Registro de asientos de compensación
    // =========================================================================

    @Test
    void recordReversalEntries_shouldCreateReversalEntries() {
        // Given
        PaymentReversedEvent event = new PaymentReversedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                "ACCOUNT_SERVICE_TIMEOUT",
                Instant.now()
        );

        when(ledgerEntryRepository.findByTransactionId(transactionId))
                .thenReturn(List.of()); // No reversals yet

        LedgerEntry reversalCredit = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .accountId(sourceAccountId)
                .entryType(EntryType.REVERSAL_CREDIT)
                .amount(amount)
                .currency(currency)
                .build();

        LedgerEntry reversalDebit = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .accountId(destinationAccountId)
                .entryType(EntryType.REVERSAL_DEBIT)
                .amount(amount)
                .currency(currency)
                .build();

        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenReturn(reversalCredit)
                .thenReturn(reversalDebit);

        // When
        ledgerService.recordReversalEntries(event);

        // Then
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
        verify(ledgerEventPublisher, times(1)).publishLedgerEntriesRecorded(any(LedgerEntriesRecordedEvent.class));
    }

    @Test
    void recordReversalEntries_shouldBeIdempotent() {
        // Given
        PaymentReversedEvent event = new PaymentReversedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                "FRAUD_DETECTION_REJECTED",
                Instant.now()
        );

        LedgerEntry existingReversal = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .entryType(EntryType.REVERSAL_CREDIT)
                .build();

        when(ledgerEntryRepository.findByTransactionId(transactionId))
                .thenReturn(List.of(existingReversal));

        // When
        ledgerService.recordReversalEntries(event);

        // Then
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
        verify(ledgerEventPublisher, never()).publishLedgerEntriesRecorded(any());
    }

    @Test
    void recordReversalEntries_shouldThrowExceptionForNullAmount() {
        // Given
        PaymentReversedEvent event = new PaymentReversedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                null,
                currency,
                "INSUFFICIENT_FUNDS",
                Instant.now()
        );

        when(ledgerEntryRepository.findByTransactionId(transactionId)).thenReturn(List.of());

        // When & Then
        assertThatThrownBy(() -> ledgerService.recordReversalEntries(event))
                .isInstanceOf(LedgerImbalanceException.class);
    }

    @Test
    void recordReversalEntries_shouldPublishConfirmationWithReversalFlag() {
        // Given
        PaymentReversedEvent event = new PaymentReversedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                "SAGA_COMPENSATION_TRIGGERED",
                Instant.now()
        );

        when(ledgerEntryRepository.findByTransactionId(transactionId))
                .thenReturn(List.of());

        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenReturn(LedgerEntry.builder().id(UUID.randomUUID()).build())
                .thenReturn(LedgerEntry.builder().id(UUID.randomUUID()).build());

        // When
        ledgerService.recordReversalEntries(event);

        // Then
        ArgumentCaptor<LedgerEntriesRecordedEvent> captor = ArgumentCaptor.forClass(LedgerEntriesRecordedEvent.class);
        verify(ledgerEventPublisher).publishLedgerEntriesRecorded(captor.capture());

        LedgerEntriesRecordedEvent publishedEvent = captor.getValue();
        assertThat(publishedEvent.reversalConfirmed()).isTrue();
    }

    // =========================================================================
    // getHistory: Consulta de historial completo
    // =========================================================================

    @Test
    void getHistory_shouldReturnHistoryForExistingAccount() {
        // Given
        List<LedgerEntry> entries = List.of(
                LedgerEntry.builder()
                        .id(UUID.randomUUID())
                        .accountId(sourceAccountId)
                        .entryType(EntryType.CREDIT)
                        .amount(new BigDecimal("1000.00"))
                        .currency("COP")
                        .createdAt(Instant.now())
                        .build(),
                LedgerEntry.builder()
                        .id(UUID.randomUUID())
                        .accountId(sourceAccountId)
                        .entryType(EntryType.DEBIT)
                        .amount(new BigDecimal("500.00"))
                        .currency("COP")
                        .createdAt(Instant.now().minusSeconds(3600))
                        .build()
        );

        when(ledgerEntryRepository.existsByAccountId(sourceAccountId)).thenReturn(true);
        when(ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(sourceAccountId))
                .thenReturn(entries);

        // When
        LedgerHistoryResponse history = ledgerService.getHistory(sourceAccountId);

        // Then
        assertThat(history.accountId()).isEqualTo(sourceAccountId);
        assertThat(history.entries()).hasSize(2);
        assertThat(history.totalCredits()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(history.totalDebits()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(history.netBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void getHistory_shouldThrowExceptionForNonExistentAccount() {
        // Given
        when(ledgerEntryRepository.existsByAccountId(sourceAccountId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> ledgerService.getHistory(sourceAccountId))
                .isInstanceOf(LedgerEntryNotFoundException.class);
    }

    @Test
    void getHistory_shouldCalculateTotalDebitsAndCredits() {
        // Given
        BigDecimal credit1 = new BigDecimal("1000.00");
        BigDecimal credit2 = new BigDecimal("500.00");
        BigDecimal debit1 = new BigDecimal("300.00");

        List<LedgerEntry> entries = List.of(
                LedgerEntry.builder()
                        .entryType(EntryType.CREDIT)
                        .amount(credit1)
                        .createdAt(Instant.now())
                        .build(),
                LedgerEntry.builder()
                        .entryType(EntryType.CREDIT)
                        .amount(credit2)
                        .createdAt(Instant.now().minusSeconds(1000))
                        .build(),
                LedgerEntry.builder()
                        .entryType(EntryType.DEBIT)
                        .amount(debit1)
                        .createdAt(Instant.now().minusSeconds(2000))
                        .build()
        );

        when(ledgerEntryRepository.existsByAccountId(sourceAccountId)).thenReturn(true);
        when(ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(sourceAccountId))
                .thenReturn(entries);

        // When
        LedgerHistoryResponse history = ledgerService.getHistory(sourceAccountId);

        // Then
        assertThat(history.totalCredits()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(history.totalDebits()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(history.netBalance()).isEqualByComparingTo(new BigDecimal("1200.00"));
    }

    // =========================================================================
    // getHistoryByDateRange: Consulta filtrada por fecha
    // =========================================================================

    @Test
    void getHistoryByDateRange_shouldReturnFilteredHistory() {
        // Given
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");

        List<LedgerEntry> entries = List.of(
                LedgerEntry.builder()
                        .id(UUID.randomUUID())
                        .accountId(sourceAccountId)
                        .entryType(EntryType.CREDIT)
                        .amount(new BigDecimal("1000.00"))
                        .createdAt(Instant.parse("2025-01-15T10:00:00Z"))
                        .build()
        );

        when(ledgerEntryRepository.existsByAccountId(sourceAccountId)).thenReturn(true);
        when(ledgerEntryRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                sourceAccountId, from, to))
                .thenReturn(entries);

        // When
        LedgerHistoryResponse history = ledgerService.getHistoryByDateRange(
                sourceAccountId, from, to);

        // Then
        assertThat(history.accountId()).isEqualTo(sourceAccountId);
        assertThat(history.periodFrom()).isEqualTo(from);
        assertThat(history.periodTo()).isEqualTo(to);
        assertThat(history.entries()).hasSize(1);
    }

    @Test
    void getHistoryByDateRange_shouldThrowExceptionForNonExistentAccount() {
        // Given
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");

        when(ledgerEntryRepository.existsByAccountId(sourceAccountId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> ledgerService.getHistoryByDateRange(
                sourceAccountId, from, to))
                .isInstanceOf(LedgerEntryNotFoundException.class);
    }

    // =========================================================================
    // getEntriesByTransactionId: Consulta por transacción
    // =========================================================================

    @Test
    void getEntriesByTransactionId_shouldReturnEntriesForTransaction() {
        // Given
        List<LedgerEntry> entries = List.of(
                LedgerEntry.builder()
                        .id(UUID.randomUUID())
                        .transactionId(transactionId)
                        .entryType(EntryType.DEBIT)
                        .amount(amount)
                        .build(),
                LedgerEntry.builder()
                        .id(UUID.randomUUID())
                        .transactionId(transactionId)
                        .entryType(EntryType.CREDIT)
                        .amount(amount)
                        .build()
        );

        when(ledgerEntryRepository.findByTransactionId(transactionId))
                .thenReturn(entries);

        // When
        List<LedgerEntryResponse> result = ledgerService.getEntriesByTransactionId(transactionId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).entryType()).isEqualTo(EntryType.DEBIT);
        assertThat(result.get(1).entryType()).isEqualTo(EntryType.CREDIT);
    }

    @Test
    void getEntriesByTransactionId_shouldReturnEmptyListForNonExistentTransaction() {
        // Given
        when(ledgerEntryRepository.findByTransactionId(transactionId))
                .thenReturn(List.of());

        // When
        List<LedgerEntryResponse> result = ledgerService.getEntriesByTransactionId(transactionId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getEntriesByTransactionId_shouldReturnAllEntryTypesIncludingReversals() {
        // Given
        List<LedgerEntry> entries = List.of(
                LedgerEntry.builder()
                        .id(UUID.randomUUID())
                        .transactionId(transactionId)
                        .entryType(EntryType.DEBIT)
                        .amount(amount)
                        .build(),
                LedgerEntry.builder()
                        .id(UUID.randomUUID())
                        .transactionId(transactionId)
                        .entryType(EntryType.CREDIT)
                        .amount(amount)
                        .build(),
                LedgerEntry.builder()
                        .id(UUID.randomUUID())
                        .transactionId(transactionId)
                        .entryType(EntryType.REVERSAL_DEBIT)
                        .amount(amount)
                        .build(),
                LedgerEntry.builder()
                        .id(UUID.randomUUID())
                        .transactionId(transactionId)
                        .entryType(EntryType.REVERSAL_CREDIT)
                        .amount(amount)
                        .build()
        );

        when(ledgerEntryRepository.findByTransactionId(transactionId))
                .thenReturn(entries);

        // When
        List<LedgerEntryResponse> result = ledgerService.getEntriesByTransactionId(transactionId);

        // Then
        assertThat(result).hasSize(4);
        assertThat(result).extracting("entryType")
                .containsExactly(
                        EntryType.DEBIT,
                        EntryType.CREDIT,
                        EntryType.REVERSAL_DEBIT,
                        EntryType.REVERSAL_CREDIT
                );
    }
}
