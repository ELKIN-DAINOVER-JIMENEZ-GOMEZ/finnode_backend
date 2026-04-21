package com.finnode.account.kafka;

import com.finnode.account.event.UserRegisteredEvent;
import com.finnode.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer del account-service.
 *
 * Escucha dos topics:
 *  - [user.registered]   → Crea automáticamente la cuenta bancaria del nuevo usuario.
 *  - [payment.reverse]   → Ejecuta la compensación Saga: libera los fondos reservados.
 *
 * Este consumer solo delega al AccountService. La responsabilidad de publicar
 * eventos de respuesta recae en el propio AccountService, no aquí.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventConsumer {

    private final AccountService accountService;

    // -------------------------------------------------------------------------
    // user.registered → Crear cuenta bancaria
    // -------------------------------------------------------------------------

    /**
     * Reacciona al evento publicado por auth-service cuando un usuario se registra.
     * Crea una cuenta bancaria con saldo 0 y estado ACTIVE para ese usuario.
     *
     * @param event   Payload deserializado desde Kafka.
     * @param offset  Offset del mensaje para trazabilidad en logs.
     * @param ack     Acknowledgment manual: solo confirma si el procesamiento fue exitoso.
     */
    @KafkaListener(
            topics = "user.registered",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserRegistered(
            @Payload UserRegisteredEvent event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack
    ) {
        log.info("[user.registered] Mensaje recibido | offset={} | userId={} | email={}",
                offset, event.getUserId(), event.getEmail());

        try {
            accountService.createAccount(event);
            ack.acknowledge();
            log.info("[user.registered] Cuenta creada exitosamente | userId={}", event.getUserId());

        } catch (Exception ex) {
            // No hacemos ack → el mensaje queda para reintento o Dead Letter Queue.
            log.error("[user.registered] Error al crear cuenta | userId={} | error={}",
                    event.getUserId(), ex.getMessage(), ex);
            // Re-lanzamos para que el ErrorHandler configurado en KafkaConsumerConfig
            // decida si reintentar o enviar a DLQ.
            throw ex;
        }
    }

    // -------------------------------------------------------------------------
    // payment.reverse → Compensación Saga: liberar fondos reservados
    // -------------------------------------------------------------------------

    /**
     * Reacciona al evento de reversión publicado por el payment-orchestrator
     * cuando algún paso del flujo Saga falla.
     * Libera los fondos que estaban bloqueados en reservedBalance.
     *
     * @param event         Payload con transactionId y monto a liberar.
     * @param offset        Offset para trazabilidad.
     * @param ack           Acknowledgment manual.
     */
    @KafkaListener(
            topics = "payment.reverse",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentReverse(
            @Payload ReleaseReserveMessage event,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack
    ) {
        log.info("[payment.reverse] Compensación Saga recibida | offset={} | transactionId={} | accountId={}",
                offset, event.getTransactionId(), event.getAccountId());

        try {
            // El AccountService ejecuta la lógica y publica FundsReleasedEvent internamente.
            accountService.releaseFunds(
                    event.getAccountId(),
                    event.getTransactionId(),
                    event.getAmount()
            );

            ack.acknowledge();

            log.info("[payment.reverse] Fondos liberados | transactionId={} | amount={}",
                    event.getTransactionId(), event.getAmount());

        } catch (Exception ex) {
            log.error("[payment.reverse] Error al liberar fondos | transactionId={} | error={}",
                    event.getTransactionId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // -------------------------------------------------------------------------
    // DTO interno para el mensaje payment.reverse
    // -------------------------------------------------------------------------

    /**
     * Representa el payload del topic [payment.reverse].
     * Se define aquí como clase estática para mantener la cohesión del consumer,
     * pero puede moverse a la carpeta event/ si otros componentes lo necesitan.
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReleaseReserveMessage {
        private String transactionId;
        private java.util.UUID accountId;
        private java.math.BigDecimal amount;
        private java.time.Instant timestamp;
    }
}