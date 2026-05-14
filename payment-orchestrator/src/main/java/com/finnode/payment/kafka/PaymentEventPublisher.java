package com.finnode.payment.kafka;

import com.finnode.payment.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Publicador de eventos para el Saga de pagos.
 *
 * Encapsula la lógica de publicación a Kafka. Cada transición importante
 * del Saga publica un evento para que los demás microservicios reaccionen.
 *
 * Topics publicados:
 *  - payment.initiated   → account-service para reservar fondos
 *  - payment.completed   → ledger-service para registrar asientos
 *  - payment.reversed    → account-service y ledger-service para compensar
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publica el evento que inicia el Saga.
     *
     * Topic: payment.initiated
     * Consumidor: account-service
     *
     * Señala el inicio de la transacción y solicita a account-service
     * que reserve los fondos en la cuenta de origen.
     */
    public void publishPaymentInitiated(UUID transactionId, UUID sourceAccountId,
                                       BigDecimal amount, String currency) {
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                transactionId,
                sourceAccountId,
                amount,
                currency,
                Instant.now()
        );

        kafkaTemplate.send("payment.initiated", transactionId.toString(), event);
        log.info("Published PaymentInitiatedEvent for transaction: {}", transactionId);
    }

    /**
     * Publica el evento cuando los fondos han sido reservados.
     *
     * Topic: payment.completed
     * Consumidor: ledger-service
     *
     * Solicita a ledger-service que registre los asientos contables
     * (débito a cuenta origen, crédito a cuenta destino).
     */
    public void publishPaymentCompleted(UUID transactionId, UUID sourceAccountId,
                                       UUID destinationAccountId, BigDecimal amount,
                                       String currency) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                Instant.now()
        );

        kafkaTemplate.send("payment.completed", transactionId.toString(), event);
        log.info("Published PaymentCompletedEvent for transaction: {}", transactionId);
    }

    /**
     * Publica el evento de reversión del Saga.
     *
     * Topic: payment.reversed
     * Consumidores: account-service (libera la reserva), ledger-service (registra compensación)
     *
     * Se publica cuando hay un fallo después de haber reservado fondos.
     * Inicia la secuencia de compensación para revertir todos los cambios.
     */
    public void publishPaymentReversed(UUID transactionId, UUID sourceAccountId,
                                      UUID destinationAccountId, BigDecimal amount,
                                      String currency, String reversalReason) {
        PaymentReversedEvent event = new PaymentReversedEvent(
                transactionId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                reversalReason,
                Instant.now()
        );

        kafkaTemplate.send("payment.reversed", transactionId.toString(), event);
        log.info("Published PaymentReversedEvent for transaction: {} with reason: {}",
                transactionId, reversalReason);
    }
}

