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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestador del Patrón Saga para transacciones de pago.
 *
 * Gestiona la máquina de estados del Saga. Es el único componente que conoce
 * el flujo completo de una transacción.
 *
 * Al recibir cada evento de respuesta (FundsReserved, LedgerRecorded, etc.),
 * decide si avanzar al siguiente paso o iniciar la secuencia de compensación.
 * Actualiza el TransactionStatus y SagaStep en cada transición.
 *
 * Transiciones válidas:
 *  - PENDING → FRAUD_REJECTED (terminal)
 *  - PENDING → FUNDS_RESERVED → LEDGER_RECORDED → COMPLETED (exitoso)
 *  - PENDING → FAILED (sin fondos reservados)
 *  - FUNDS_RESERVED → REVERSED (con compensación)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final TransactionRepository transactionRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    /**
     * Procesa el evento FundsReservedEvent.
     *
     * Transición: PENDING → FUNDS_RESERVED
     * Acción: Solicita a ledger-service que registre los asientos contables
     */
    @Transactional
    public void onFundsReserved(FundsReservedEvent event) {
        log.info("Processing FundsReservedEvent for transaction: {}", event.transactionId());

        Transaction transaction = transactionRepository
                .findByTransactionId(event.transactionId())
                .orElseThrow(() -> new PaymentProcessingException(
                        "Transaction not found: " + event.transactionId()));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            log.warn("Ignoring FundsReservedEvent for transaction {} in status {}",
                    event.transactionId(), transaction.getStatus());
            return;
        }

        // Actualizar estado: fondos reservados
        transaction.setStatus(TransactionStatus.FUNDS_RESERVED);
        transaction.setCurrentStep(SagaStep.RECORD_LEDGER);
        transactionRepository.save(transaction);

        log.info("Transaction {} advanced to FUNDS_RESERVED state", event.transactionId());

        // Publicar evento para que ledger-service registre los asientos
        paymentEventPublisher.publishPaymentCompleted(
                event.transactionId(),
                transaction.getSourceAccountId(),
                transaction.getDestinationAccountId(),
                event.amount(),
                transaction.getCurrency()
        );
    }

    /**
     * Procesa el evento FundsReservationFailedEvent.
     *
     * Transición: PENDING → FAILED
     *
     * Los fondos nunca fueron reservados, así que no hay nada que compensar.
     * La transacción termina en estado FAILED.
     */
    @Transactional
    public void onFundsReservationFailed(FundsReservationFailedEvent event) {
        log.info("Processing FundsReservationFailedEvent for transaction: {}", event.transactionId());

        Transaction transaction = transactionRepository
                .findByTransactionId(event.transactionId())
                .orElseThrow(() -> new PaymentProcessingException(
                        "Transaction not found: " + event.transactionId()));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            log.warn("Ignoring FundsReservationFailedEvent for transaction {} in status {}",
                    event.transactionId(), transaction.getStatus());
            return;
        }

        // Actualizar estado: falló la reserva de fondos
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setCurrentStep(SagaStep.COMPENSATE);
        transaction.setFailureReason(event.reason());
        transactionRepository.save(transaction);

        log.warn("Transaction {} failed at funds reservation: {}",
                event.transactionId(), event.reason());

        // No publicamos payment.reversed porque nunca hubo fondos reservados
        // El frontend recibirá la notificación vía WebSocket del estado FAILED
    }

    /**
     * Procesa el evento LedgerEntriesRecordedEvent.
     *
     * Transición: FUNDS_RESERVED → COMPLETED
     *
     * Los asientos contables han sido registrados exitosamente.
     * El Saga ha completado todas sus fases.
     */
    @Transactional
    public void onLedgerRecorded(LedgerEntriesRecordedEvent event) {
        log.info("Processing LedgerEntriesRecordedEvent for transaction: {}", event.transactionId());

        Transaction transaction = transactionRepository
                .findByTransactionId(event.transactionId())
                .orElseThrow(() -> new PaymentProcessingException(
                        "Transaction not found: " + event.transactionId()));

        if (transaction.getStatus() != TransactionStatus.FUNDS_RESERVED) {
            log.warn("Ignoring LedgerEntriesRecordedEvent for transaction {} in status {}",
                    event.transactionId(), transaction.getStatus());
            return;
        }

        // Actualizar estado: transacción completada
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCurrentStep(SagaStep.CONFIRM_PAYMENT);
        transactionRepository.save(transaction);

        log.info("Transaction {} successfully completed", event.transactionId());

        // El frontend recibirá la notificación vía WebSocket del estado COMPLETED
    }

    /**
     * Procesa el evento LedgerRecordingFailedEvent (si los ledger no registran).
     *
     * Transición: FUNDS_RESERVED → REVERSED
     *
     * Los fondos ya fueron reservados, pero el registro contable falló.
     * Iniciamos compensación: liberamos la reserva de fondos y registramos
     * asientos de compensación.
     */
    @Transactional
    public void compensateTransaction(String transactionId, String reason) {
        log.warn("Initiating compensation for transaction {} due to: {}", transactionId, reason);

        Transaction transaction = transactionRepository
                .findByTransactionId(java.util.UUID.fromString(transactionId))
                .orElseThrow(() -> new PaymentProcessingException(
                        "Transaction not found: " + transactionId));

        if (transaction.getStatus() != TransactionStatus.FUNDS_RESERVED) {
            log.warn("Cannot compensate transaction {} in status {}. No funds were reserved.",
                    transactionId, transaction.getStatus());
            return;
        }

        // Actualizar estado: transacción revertida
        transaction.setStatus(TransactionStatus.REVERSED);
        transaction.setCurrentStep(SagaStep.COMPENSATE);
        transaction.setFailureReason(reason);
        transactionRepository.save(transaction);

        log.info("Transaction {} marked as REVERSED", transactionId);

        // Publicar evento de reversión para que account-service y ledger-service
        // realicen las operaciones de compensación
        paymentEventPublisher.publishPaymentReversed(
                transaction.getTransactionId(),
                transaction.getSourceAccountId(),
                transaction.getDestinationAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                reason
        );
    }

    /**
     * Rechaza una transacción detectada como fraude.
     *
     * Transición: PENDING → FRAUD_REJECTED
     *
     * El Saga nunca inicia, por lo que no hay compensación necesaria.
     */
    @Transactional
    public void rejectAsFraud(Transaction transaction, double riskScore, String reason) {
        log.warn("Rejecting transaction {} as fraud. Risk score: {}",
                transaction.getTransactionId(), riskScore);

        transaction.setStatus(TransactionStatus.FRAUD_REJECTED);
        transaction.setCurrentStep(SagaStep.FRAUD_CHECK);
        transaction.setFraudRiskScore(riskScore);
        transaction.setFailureReason(reason);
        transactionRepository.save(transaction);

        log.info("Transaction {} rejected as fraud", transaction.getTransactionId());
    }

    /**
     * Inicia el Saga después de pasar la validación de fraude.
     *
     * Transición: PENDING → PENDING (con currentStep ajustado)
     *
     * Publica el evento PaymentInitiatedEvent para que account-service
     * inicie la reserva de fondos.
     */
    @Transactional
    public void startSaga(Transaction transaction) {
        log.info("Starting Saga for transaction {}", transaction.getTransactionId());

        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCurrentStep(SagaStep.RESERVE_FUNDS);
        transactionRepository.save(transaction);

        // Publicar evento para que account-service reserve los fondos
        paymentEventPublisher.publishPaymentInitiated(
                transaction.getTransactionId(),
                transaction.getSourceAccountId(),
                transaction.getAmount(),
                transaction.getCurrency()
        );

        log.info("PaymentInitiatedEvent published for transaction {}", transaction.getTransactionId());
    }
}


