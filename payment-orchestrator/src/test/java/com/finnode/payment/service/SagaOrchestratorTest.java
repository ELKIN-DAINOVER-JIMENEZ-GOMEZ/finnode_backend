package com.finnode.payment.service;

import com.finnode.payment.event.FundsReservationFailedEvent;
import com.finnode.payment.event.FundsReservedEvent;
import com.finnode.payment.event.LedgerEntriesRecordedEvent;
import com.finnode.payment.exception.PaymentProcessingException;
import com.finnode.payment.kafka.PaymentEventPublisher;
import com.finnode.payment.model.SagaStep;
import com.finnode.payment.model.Transaction;
import com.finnode.payment.model.TransactionStatus;
import com.finnode.payment.repository.TransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link SagaOrchestrator}.
 *
 * Campos reales confirmados de los eventos (com.finnode.payment.event):
 *   - FundsReservedEvent(UUID transactionId, UUID accountId, BigDecimal amount, Instant timestamp)
 *   - FundsReservationFailedEvent(UUID transactionId, UUID accountId, String reason, Instant timestamp)
 *   - LedgerEntriesRecordedEvent(UUID transactionId, UUID debitEntryId, UUID creditEntryId,
 *       BigDecimal amount, String currency, boolean reversalConfirmed, Instant timestamp)
 * Y de PaymentEventPublisher (com.finnode.payment.kafka):
 *   - publishPaymentCompleted(transactionId, sourceAccountId, destinationAccountId, amount, currency)
 *   - publishPaymentReversed(transactionId, sourceAccountId, destinationAccountId, amount, currency, reason)
 *   - publishPaymentInitiated(transactionId, sourceAccountId, amount, currency)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SagaOrchestrator")
class SagaOrchestratorTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    private SagaOrchestrator sagaOrchestrator;

    private Transaction transaction;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        sagaOrchestrator = new SagaOrchestrator(transactionRepository, paymentEventPublisher);

        transactionId = UUID.randomUUID();
        transaction = Transaction.builder()
                .transactionId(transactionId)
                .userId(UUID.randomUUID())
                .sourceAccountId(UUID.randomUUID())
                .destinationAccountId(UUID.randomUUID())
                .amount(new BigDecimal("1000.00"))
                .currency("COP")
                .status(TransactionStatus.PENDING)
                .currentStep(SagaStep.RESERVE_FUNDS)
                .build();
    }

    // ---------------------------------------------------------------
    // onFundsReserved
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Debe avanzar a FUNDS_RESERVED y publicar el evento de ledger cuando la transacción está PENDING")
    void onFundsReserved_whenPending_shouldAdvanceAndPublish() {
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(transaction));

        FundsReservedEvent event = new FundsReservedEvent(
                transactionId, transaction.getSourceAccountId(), transaction.getAmount(), Instant.now());
        sagaOrchestrator.onFundsReserved(event);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FUNDS_RESERVED);
        assertThat(transaction.getCurrentStep()).isEqualTo(SagaStep.RECORD_LEDGER);

        verify(transactionRepository).save(transaction);
        verify(paymentEventPublisher).publishPaymentCompleted(
                transactionId,
                transaction.getSourceAccountId(),
                transaction.getDestinationAccountId(),
                transaction.getAmount(),
                transaction.getCurrency()
        );
    }

    @Test
    @DisplayName("Debe ignorar FundsReservedEvent si la transacción no está en PENDING (idempotencia)")
    void onFundsReserved_whenNotPending_shouldBeIgnored() {
        transaction.setStatus(TransactionStatus.FUNDS_RESERVED);
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(transaction));

        FundsReservedEvent event = new FundsReservedEvent(
                transactionId, transaction.getSourceAccountId(), transaction.getAmount(), Instant.now());
        sagaOrchestrator.onFundsReserved(event);

        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    @DisplayName("Debe lanzar PaymentProcessingException si la transacción no existe")
    void onFundsReserved_whenTransactionNotFound_shouldThrow() {
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.empty());

        FundsReservedEvent event = new FundsReservedEvent(
                transactionId, UUID.randomUUID(), new BigDecimal("1000.00"), Instant.now());

        assertThatThrownBy(() -> sagaOrchestrator.onFundsReserved(event))
                .isInstanceOf(PaymentProcessingException.class);

        verifyNoInteractions(paymentEventPublisher);
    }

    // ---------------------------------------------------------------
    // onFundsReservationFailed
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Debe marcar la transacción como FAILED sin publicar compensación")
    void onFundsReservationFailed_whenPending_shouldMarkFailed() {
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(transaction));

        FundsReservationFailedEvent event = new FundsReservationFailedEvent(
                transactionId, transaction.getSourceAccountId(), "Saldo insuficiente", Instant.now());
        sagaOrchestrator.onFundsReservationFailed(event);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.getCurrentStep()).isEqualTo(SagaStep.COMPENSATE);
        assertThat(transaction.getFailureReason()).isEqualTo("Saldo insuficiente");

        verify(transactionRepository).save(transaction);
        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    @DisplayName("Debe ignorar FundsReservationFailedEvent si la transacción no está en PENDING")
    void onFundsReservationFailed_whenNotPending_shouldBeIgnored() {
        transaction.setStatus(TransactionStatus.COMPLETED);
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(transaction));

        FundsReservationFailedEvent event = new FundsReservationFailedEvent(
                transactionId, transaction.getSourceAccountId(), "irrelevante", Instant.now());
        sagaOrchestrator.onFundsReservationFailed(event);

        verify(transactionRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // onLedgerRecorded
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Debe completar la transacción cuando estaba en FUNDS_RESERVED")
    void onLedgerRecorded_whenFundsReserved_shouldComplete() {
        transaction.setStatus(TransactionStatus.FUNDS_RESERVED);
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(transaction));

        LedgerEntriesRecordedEvent event = new LedgerEntriesRecordedEvent(
                transactionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                transaction.getAmount(),
                transaction.getCurrency(),
                false,
                Instant.now()
        );
        sagaOrchestrator.onLedgerRecorded(event);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.getCurrentStep()).isEqualTo(SagaStep.CONFIRM_PAYMENT);
        verify(transactionRepository).save(transaction);
    }

    @Test
    @DisplayName("Debe ignorar LedgerEntriesRecordedEvent si la transacción no está en FUNDS_RESERVED")
    void onLedgerRecorded_whenNotFundsReserved_shouldBeIgnored() {
        transaction.setStatus(TransactionStatus.PENDING);
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(transaction));

        LedgerEntriesRecordedEvent event = new LedgerEntriesRecordedEvent(
                transactionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                transaction.getAmount(),
                transaction.getCurrency(),
                false,
                Instant.now()
        );
        sagaOrchestrator.onLedgerRecorded(event);

        verify(transactionRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // compensateTransaction
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Debe revertir la transacción y publicar el evento de reversión cuando estaba FUNDS_RESERVED")
    void compensateTransaction_whenFundsReserved_shouldReverseAndPublish() {
        transaction.setStatus(TransactionStatus.FUNDS_RESERVED);
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(transaction));

        sagaOrchestrator.compensateTransaction(transactionId.toString(), "Timeout en ledger-service");

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(transaction.getCurrentStep()).isEqualTo(SagaStep.COMPENSATE);
        assertThat(transaction.getFailureReason()).isEqualTo("Timeout en ledger-service");

        verify(transactionRepository).save(transaction);
        verify(paymentEventPublisher).publishPaymentReversed(
                transactionId,
                transaction.getSourceAccountId(),
                transaction.getDestinationAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                "Timeout en ledger-service"
        );
    }

    @Test
    @DisplayName("No debe compensar (ni publicar) si los fondos nunca fueron reservados")
    void compensateTransaction_whenNotFundsReserved_shouldDoNothing() {
        transaction.setStatus(TransactionStatus.PENDING);
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(transaction));

        sagaOrchestrator.compensateTransaction(transactionId.toString(), "cualquier razón");

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    @DisplayName("Debe lanzar PaymentProcessingException si la transacción a compensar no existe")
    void compensateTransaction_whenTransactionNotFound_shouldThrow() {
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                sagaOrchestrator.compensateTransaction(transactionId.toString(), "razón"))
                .isInstanceOf(PaymentProcessingException.class);
    }

    // ---------------------------------------------------------------
    // rejectAsFraud
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Debe marcar la transacción como FRAUD_REJECTED con el score y la razón dados")
    void rejectAsFraud_shouldMarkTransactionAsFraudRejected() {
        sagaOrchestrator.rejectAsFraud(transaction, 0.95, "Patrón de fraude detectado");

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FRAUD_REJECTED);
        assertThat(transaction.getCurrentStep()).isEqualTo(SagaStep.FRAUD_CHECK);
        assertThat(transaction.getFraudRiskScore()).isEqualTo(0.95);
        assertThat(transaction.getFailureReason()).isEqualTo("Patrón de fraude detectado");

        verify(transactionRepository).save(transaction);
        verifyNoInteractions(paymentEventPublisher);
    }

    // ---------------------------------------------------------------
    // startSaga
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Debe fijar el estado PENDING/RESERVE_FUNDS y publicar PaymentInitiatedEvent")
    void startSaga_shouldSetStepAndPublishInitiatedEvent() {
        sagaOrchestrator.startSaga(transaction);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.getCurrentStep()).isEqualTo(SagaStep.RESERVE_FUNDS);

        verify(transactionRepository).save(transaction);
        verify(paymentEventPublisher).publishPaymentInitiated(
                transaction.getTransactionId(),
                transaction.getSourceAccountId(),
                transaction.getAmount(),
                transaction.getCurrency()
        );
    }
}