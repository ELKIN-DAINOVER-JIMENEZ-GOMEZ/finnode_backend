package com.finnode.ledger.kafka;

import com.finnode.ledger.event.PaymentCompletedEvent;
import com.finnode.ledger.event.PaymentReversedEvent;
import com.finnode.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumer de eventos Kafka del ledger-service.
 *
 * Escucha dos topics:
 *   [payment.completed] → dispara el registro de asientos DEBIT + CREDIT
 *   [payment.reversed]  → dispara el registro de asientos REVERSAL_DEBIT + REVERSAL_CREDIT
 *
 * PATRÓN DE ACKNOWLEDGMENT MANUAL:
 * Se usa AckMode.MANUAL para que el offset en Kafka solo avance cuando
 * LedgerService confirma que los asientos fueron persistidos exitosamente.
 * Si ocurre cualquier excepción antes del ack, Kafka reentrega el mensaje.
 * Esto garantiza que ningún movimiento financiero se pierda silenciosamente.
 *
 * DEAD LETTER QUEUE (DLQ):
 * Si un mensaje falla 3 veces consecutivas (configurado en KafkaConsumerConfig),
 * Kafka lo mueve automáticamente al topic [payment.completed.DLT] o
 * [payment.reversed.DLT] para revisión manual. Esto evita que un mensaje
 * corrupto bloquee indefinidamente el procesamiento de los demás.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerEventConsumer {

    private final LedgerService ledgerService;

    /**
     * Escucha el topic [payment.completed] y registra los asientos contables
     * correspondientes a una transferencia exitosa.
     *
     * Flujo:
     *   1. Recibe el PaymentCompletedEvent deserializado desde JSON
     *   2. Delega a LedgerService.recordEntries() para crear DEBIT + CREDIT
     *   3. Si todo OK → hace ack manual para avanzar el offset en Kafka
     *   4. Si falla   → no hace ack → Kafka reentrega el mensaje para reintento
     *
     * @param event   payload del evento deserializado
     * @param offset  offset del mensaje en la partición (para logging)
     * @param ack     mecanismo de confirmación manual del offset
     */
    @KafkaListener(
            topics = "payment.completed",
            groupId = "ledger-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentCompleted(
            @Payload PaymentCompletedEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack
    ) {
        log.info("[KAFKA] Evento recibido: payment.completed | transactionId={} | offset={}",
                event.transactionId(), offset);

        try {
            ledgerService.recordEntries(event);
            ack.acknowledge();
            log.info("[KAFKA] Asientos registrados y offset confirmado | transactionId={}",
                    event.transactionId());

        } catch (Exception ex) {
            log.error("[KAFKA] Error procesando payment.completed | transactionId={} | error={}",
                    event.transactionId(), ex.getMessage(), ex);
            // No se llama ack.acknowledge() → Kafka reentregará el mensaje
            // Después de 3 reintentos (configurado en KafkaConsumerConfig) → va a DLQ
        }
    }

    /**
     * Escucha el topic [payment.reversed] y registra los asientos de compensación
     * correspondientes a una reversión del Patrón Saga.
     *
     * Flujo:
     *   1. Recibe el PaymentReversedEvent deserializado desde JSON
     *   2. Delega a LedgerService.recordReversalEntries() para crear
     *      REVERSAL_DEBIT + REVERSAL_CREDIT
     *   3. Si todo OK → hace ack manual para avanzar el offset en Kafka
     *   4. Si falla   → no hace ack → Kafka reentrega el mensaje para reintento
     *
     * @param event   payload del evento deserializado
     * @param offset  offset del mensaje en la partición (para logging)
     * @param ack     mecanismo de confirmación manual del offset
     */
    @KafkaListener(
            topics = "payment.reversed",
            groupId = "ledger-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentReversed(
            @Payload PaymentReversedEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack
    ) {
        log.info("[KAFKA] Evento recibido: payment.reversed | transactionId={} | reason={} | offset={}",
                event.transactionId(), event.reversalReason(), offset);

        try {
            ledgerService.recordReversalEntries(event);
            ack.acknowledge();
            log.info("[KAFKA] Asientos de reversión registrados y offset confirmado | transactionId={}",
                    event.transactionId());

        } catch (Exception ex) {
            log.error("[KAFKA] Error procesando payment.reversed | transactionId={} | error={}",
                    event.transactionId(), ex.getMessage(), ex);
            // No se llama ack.acknowledge() → Kafka reentregará el mensaje
        }
    }
}