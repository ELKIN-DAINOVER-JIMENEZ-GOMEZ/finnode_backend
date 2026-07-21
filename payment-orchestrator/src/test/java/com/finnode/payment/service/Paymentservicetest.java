package com.finnode.payment.service;

import com.finnode.payment.dto.FraudEvaluationResult;
import com.finnode.payment.dto.TransactionStatusResponse;
import com.finnode.payment.dto.TransferRequest;
import com.finnode.payment.dto.TransferResponse;
import com.finnode.payment.exception.FraudDetectedException;
import com.finnode.payment.exception.TransactionNotFoundException;
import com.finnode.payment.model.SagaStep;
import com.finnode.payment.model.Transaction;
import com.finnode.payment.model.TransactionStatus;
import com.finnode.payment.repository.TransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link PaymentService}.
 *
 * Se mockean TransactionRepository, FraudDetectionService y SagaOrchestrator
 * para aislar por completo la lógica de orquestación que vive en el servicio.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private FraudDetectionService fraudDetectionService;

    @Mock
    private SagaOrchestrator sagaOrchestrator;

    private PaymentService paymentService;

    private UUID userId;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private TransferRequest validRequest;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(transactionRepository, fraudDetectionService, sagaOrchestrator);

        userId = UUID.randomUUID();
        sourceAccountId = UUID.randomUUID();
        destinationAccountId = UUID.randomUUID();

        validRequest = new TransferRequest(
                sourceAccountId.toString(),
                destinationAccountId.toString(),
                new BigDecimal("100000.00"),
                "COP",
                "Pago de arriendo"
        );
    }

    // ---------------------------------------------------------------
    // initiateTransfer
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Debe iniciar la transferencia cuando la evaluación de fraude aprueba la transacción")
    void initiateTransfer_whenFraudCheckPasses_shouldPersistAndStartSaga() {
        FraudEvaluationResult approvedResult = new FraudEvaluationResult(true, 0.12, "Riesgo bajo");

        when(fraudDetectionService.evaluate(any(Transaction.class))).thenReturn(approvedResult);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResponse response = paymentService.initiateTransfer(validRequest, userId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(response.amount()).isEqualByComparingTo(validRequest.amount());
        assertThat(response.currency()).isEqualTo(validRequest.currency());
        assertThat(response.transactionId()).isNotBlank();

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction saved = txCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getSourceAccountId()).isEqualTo(sourceAccountId);
        assertThat(saved.getDestinationAccountId()).isEqualTo(destinationAccountId);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.PENDING);

        verify(sagaOrchestrator).startSaga(saved);
        verify(sagaOrchestrator, never()).rejectAsFraud(any(), anyDouble(), anyString());
    }

    @Test
    @DisplayName("Debe rechazar la transferencia cuando source y destination son la misma cuenta")
    void initiateTransfer_whenSameAccount_shouldThrowIllegalArgumentException() {
        TransferRequest selfTransfer = new TransferRequest(
                sourceAccountId.toString(),
                sourceAccountId.toString(),
                new BigDecimal("50.00"),
                "COP",
                "auto-transferencia"
        );

        assertThatThrownBy(() -> paymentService.initiateTransfer(selfTransfer, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("misma cuenta");

        verifyNoInteractions(fraudDetectionService, sagaOrchestrator);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe persistir y rechazar por fraude, relanzando FraudDetectedException")
    void initiateTransfer_whenFraudDetected_shouldPersistRejectAndRethrow() {
        FraudDetectedException fraudException = new FraudDetectedException(0.91, "Monto atípico");

        when(fraudDetectionService.evaluate(any(Transaction.class))).thenThrow(fraudException);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> paymentService.initiateTransfer(validRequest, userId))
                .isInstanceOf(FraudDetectedException.class)
                .isSameAs(fraudException);

        verify(transactionRepository).save(any(Transaction.class));
        verify(sagaOrchestrator).rejectAsFraud(any(Transaction.class), eq(0.91), eq("Monto atípico"));
        verify(sagaOrchestrator, never()).startSaga(any());
    }

    @Test
    @DisplayName("Debe envolver cualquier error inesperado en RuntimeException")
    void initiateTransfer_whenUnexpectedError_shouldWrapInRuntimeException() {
        when(fraudDetectionService.evaluate(any(Transaction.class)))
                .thenThrow(new IllegalStateException("Fallo de conexión con el modelo de IA"));

        assertThatThrownBy(() -> paymentService.initiateTransfer(validRequest, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error iniciando transferencia")
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(sagaOrchestrator, never()).startSaga(any());
        verify(sagaOrchestrator, never()).rejectAsFraud(any(), anyDouble(), anyString());
    }

    @Test
    @DisplayName("El mensaje de dominio inválido en sourceAccountId debe propagar IllegalArgumentException")
    void initiateTransfer_whenAccountIdIsNotUuid_shouldThrowIllegalArgumentException() {
        TransferRequest malformed = new TransferRequest(
                "no-es-un-uuid",
                destinationAccountId.toString(),
                new BigDecimal("10.00"),
                "USD",
                null
        );

        assertThatThrownBy(() -> paymentService.initiateTransfer(malformed, userId))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(fraudDetectionService, sagaOrchestrator);
    }

    // ---------------------------------------------------------------
    // getTransactionStatus
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Debe retornar el estado de la transacción cuando existe")
    void getTransactionStatus_whenExists_shouldReturnResponse() {
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .transactionId(transactionId)
                .userId(userId)
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(destinationAccountId)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .status(TransactionStatus.FUNDS_RESERVED)
                .currentStep(SagaStep.RECORD_LEDGER)
                .fraudRiskScore(0.2)
                .build();

        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(transaction));

        TransactionStatusResponse response = paymentService.getTransactionStatus(transactionId);

        assertThat(response.transactionId()).isEqualTo(transactionId.toString());
        assertThat(response.status()).isEqualTo(TransactionStatus.FUNDS_RESERVED);
        assertThat(response.currentStep()).isEqualTo(SagaStep.RECORD_LEDGER);
        assertThat(response.fraudRiskScore()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("Debe lanzar TransactionNotFoundException cuando la transacción no existe")
    void getTransactionStatus_whenNotFound_shouldThrow() {
        UUID transactionId = UUID.randomUUID();
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getTransactionStatus(transactionId))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(transactionId.toString());
    }

    // ---------------------------------------------------------------
    // getUserTransactionHistory
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Debe mapear correctamente el historial de transacciones del usuario")
    void getUserTransactionHistory_shouldReturnMappedList() {
        Transaction t1 = Transaction.builder()
                .transactionId(UUID.randomUUID())
                .userId(userId)
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(destinationAccountId)
                .amount(new BigDecimal("10.00"))
                .currency("COP")
                .status(TransactionStatus.COMPLETED)
                .currentStep(SagaStep.CONFIRM_PAYMENT)
                .build();
        Transaction t2 = Transaction.builder()
                .transactionId(UUID.randomUUID())
                .userId(userId)
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(destinationAccountId)
                .amount(new BigDecimal("20.00"))
                .currency("COP")
                .status(TransactionStatus.FAILED)
                .currentStep(SagaStep.COMPENSATE)
                .build();

        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(t1, t2));

        List<TransactionStatusResponse> history = paymentService.getUserTransactionHistory(userId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).transactionId()).isEqualTo(t1.getTransactionId().toString());
        assertThat(history.get(1).status()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    @DisplayName("Debe retornar lista vacía cuando el usuario no tiene transacciones")
    void getUserTransactionHistory_whenEmpty_shouldReturnEmptyList() {
        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        List<TransactionStatusResponse> history = paymentService.getUserTransactionHistory(userId);

        assertThat(history).isEmpty();
    }

    // ---------------------------------------------------------------
    // getTransactionsByStatus
    // ---------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(TransactionStatus.class)
    @DisplayName("Debe delegar en el repositorio para cada estado posible")
    void getTransactionsByStatus_shouldDelegateToRepository(TransactionStatus status) {
        when(transactionRepository.findByStatus(status)).thenReturn(List.of());

        List<Transaction> result = paymentService.getTransactionsByStatus(status);

        assertThat(result).isEmpty();
        verify(transactionRepository).findByStatus(status);
    }
}