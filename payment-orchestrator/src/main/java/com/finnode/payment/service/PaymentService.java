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
import com.finnode.payment.service.FraudDetectionService;
import com.finnode.payment.service.SagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio principal de pagos.
 *
 * Orquesta el punto de entrada: recibe el TransferRequest, consulta a
 * FraudDetectionService, crea la entidad Transaction en base de datos,
 * y arranca el Saga publicando el primer evento.
 *
 * También atiende consultas de estado e historial de transacciones.
 *
 * Flujo:
 * 1. Recibe solicitud de transferencia (POST /payments/transfer)
 * 2. Valida que sourceAccountId != destinationAccountId
 * 3. Consulta account-service: verifica que ambas cuentas existen y están ACTIVE
 * 4. Llama a FraudDetectionService.evaluate()
 *    └─ Si riskScore >= threshold → FraudDetectedException (HTTP 422)
 * 5. Persiste Transaction (status: PENDING, step: RESERVE_FUNDS)
 * 6. Llama a SagaOrchestrator.startSaga() → publica PaymentInitiatedEvent
 * 7. Retorna TransferResponse con transactionId y status PENDING (HTTP 202 Accepted)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final FraudDetectionService fraudDetectionService;
    private final SagaOrchestrator sagaOrchestrator;

    /**
     * Inicia una transferencia entre cuentas.
     *
     * Entrada: TransferRequest con sourceAccountId, destinationAccountId, amount, currency, description
     * Salida: TransferResponse con transactionId, status PENDING (HTTP 202 Accepted)
     *
     * Flujo:
     * 1. Validar que no es una transferencia a la misma cuenta
     * 2. Evaluar fraude
     * 3. Crear transacción en BD
     * 4. Arrancar el Saga
     *
     * @param request los datos de la transferencia
     * @param userId el usuario autenticado (extraído del JWT)
     * @return TransferResponse con datos de la transacción iniciada
     * @throws IllegalArgumentException si sourceAccountId == destinationAccountId
     * @throws FraudDetectedException si el riesgo supera el umbral
     */
    @Transactional
    public TransferResponse initiateTransfer(TransferRequest request, UUID userId) {
        log.info("Initiating transfer for user: {}", userId);

        // Convertir strings a UUIDs
        UUID sourceAccountId = UUID.fromString(request.sourceAccountId());
        UUID destinationAccountId = UUID.fromString(request.destinationAccountId());

        // Validar que no sea una transferencia a la misma cuenta
        if (sourceAccountId.equals(destinationAccountId)) {
            log.warn("User {} attempted self-transfer", userId);
            throw new IllegalArgumentException("No puedes transferir a la misma cuenta");
        }

        // TODO: En producción, consultar account-service via Feign para validar que las
        // cuentas existen y están activas. Por ahora, asumimos que son válidas.

        // Crear entidad de transacción
        Transaction transaction = Transaction.builder()
                .transactionId(UUID.randomUUID())
                .userId(userId)
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(destinationAccountId)
                .amount(request.amount())
                .currency(request.currency())
                .description(request.description())
                .status(TransactionStatus.PENDING)
                .currentStep(SagaStep.FRAUD_CHECK)
                .build();

        log.debug("Created transaction entity: {}", transaction.getTransactionId());

        // Evaluar fraude
        try {
            FraudEvaluationResult fraudResult = fraudDetectionService.evaluate(transaction);
            log.info("Fraud evaluation passed for transaction: {}", transaction.getTransactionId());

            // Si llegamos aquí, la transacción pasó la evaluación de fraude
            // Persistir la transacción
            transaction = transactionRepository.save(transaction);

            // Arrancar el Saga
            sagaOrchestrator.startSaga(transaction);

            // Retornar respuesta
            return new TransferResponse(
                    transaction.getTransactionId().toString(),
                    TransactionStatus.PENDING,
                    request.amount(),
                    request.currency(),
                    LocalDateTime.now(),
                    "Transferencia iniciada. Estado: PENDING"
            );

        } catch (FraudDetectedException e) {
            log.warn("Transfer rejected by fraud detection for user {}: {}", userId, e.getMessage());

            // Persistir la transacción con estado FRAUD_REJECTED
            transaction = transactionRepository.save(transaction);
            sagaOrchestrator.rejectAsFraud(transaction, e.getRiskScore(), e.getReason());

            // Re-lanzar la excepción para que el controller retorne HTTP 422
            throw e;

        } catch (Exception e) {
            log.error("Unexpected error initiating transfer for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Error iniciando transferencia", e);
        }
    }

    /**
     * Consulta el estado de una transacción.
     *
     * Entrada: transactionId (UUID)
     * Salida: TransactionStatusResponse con estado actual, step, scores, etc.
     *
     * @param transactionId el identificador de la transacción
     * @return TransactionStatusResponse con detalles de la transacción
     * @throws TransactionNotFoundException si no existe la transacción
     */
    @Transactional(readOnly = true)
    public TransactionStatusResponse getTransactionStatus(UUID transactionId) {
        log.debug("Querying status for transaction: {}", transactionId);

        Transaction transaction = transactionRepository
                .findByTransactionId(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId.toString()));

        return new TransactionStatusResponse(
                transaction.getTransactionId().toString(),
                transaction.getStatus(),
                transaction.getCurrentStep(),
                transaction.getFraudRiskScore(),
                transaction.getFailureReason(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }

    /**
     * Obtiene el historial de transacciones del usuario autenticado.
     *
     * Retorna todas las transacciones del usuario, ordenadas por fecha
     * de creación descendente (más reciente primero).
     *
     * @param userId el identificador del usuario
     * @return lista de TransactionStatusResponse
     */
    @Transactional(readOnly = true)
    public List<TransactionStatusResponse> getUserTransactionHistory(UUID userId) {
        log.debug("Querying transaction history for user: {}", userId);

        return transactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(transaction -> new TransactionStatusResponse(
                        transaction.getTransactionId().toString(),
                        transaction.getStatus(),
                        transaction.getCurrentStep(),
                        transaction.getFraudRiskScore(),
                        transaction.getFailureReason(),
                        transaction.getCreatedAt(),
                        transaction.getUpdatedAt()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todas las transacciones en un estado específico.
     *
     * Útil para diagnóstico y monitoring.
     *
     * @param status el TransactionStatus a filtrar
     * @return lista de transacciones en ese estado
     */
    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByStatus(TransactionStatus status) {
        log.debug("Querying transactions with status: {}", status);
        return transactionRepository.findByStatus(status);
    }
}

