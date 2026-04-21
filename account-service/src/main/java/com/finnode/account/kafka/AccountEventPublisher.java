package com.finnode.account.kafka;

import com.finnode.account.event.FundsReleasedEvent;
import com.finnode.account.event.FundsReservationFailedEvent;
import com.finnode.account.event.FundsReservedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka Publisher del account-service.
 *
 * Encapsula el KafkaTemplate y expone métodos semánticamente nombrados
 * para cada evento que este servicio puede publicar:
 *
 *  - publishFundsReserved()       → [account.funds-reserved]
 *  - publishReservationFailed()   → [account.funds-reservation-failed]
 *  - publishFundsReleased()       → [account.funds-released]
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventPublisher {

    private static final String TOPIC_FUNDS_RESERVED          = "account.funds-reserved";
    private static final String TOPIC_RESERVATION_FAILED      = "account.funds-reservation-failed";
    private static final String TOPIC_FUNDS_RELEASED          = "account.funds-released";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // -------------------------------------------------------------------------
    // Publicar: fondos reservados exitosamente
    // -------------------------------------------------------------------------

    /**
     * Publica un FundsReservedEvent al topic [account.funds-reserved].
     * El payment-orchestrator lo consume como señal verde para continuar el Saga.
     *
     * @param event Evento con transactionId, accountId, amount y timestamp.
     */
    public void publishFundsReserved(FundsReservedEvent event) {
        String key = event.getTransactionId();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_FUNDS_RESERVED, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[{}] Error al publicar FundsReservedEvent | transactionId={} | error={}",
                        TOPIC_FUNDS_RESERVED, key, ex.getMessage(), ex);
            } else {
                log.info("[{}] FundsReservedEvent publicado | transactionId={} | partition={} | offset={}",
                        TOPIC_FUNDS_RESERVED, key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Publicar: reserva fallida (saldo insuficiente u otro error)
    // -------------------------------------------------------------------------

    /**
     * Publica un FundsReservationFailedEvent al topic [account.funds-reservation-failed].
     * El payment-orchestrator lo consume para iniciar la compensación Saga.
     *
     * @param event Evento con transactionId, accountId, reason y timestamp.
     */
    public void publishReservationFailed(FundsReservationFailedEvent event) {
        String key = event.getTransactionId();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_RESERVATION_FAILED, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[{}] Error al publicar FundsReservationFailedEvent | transactionId={} | error={}",
                        TOPIC_RESERVATION_FAILED, key, ex.getMessage(), ex);
            } else {
                log.info("[{}] FundsReservationFailedEvent publicado | transactionId={} | reason={} | partition={} | offset={}",
                        TOPIC_RESERVATION_FAILED, key, event.getReason(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Publicar: fondos liberados (compensación Saga completada)
    // -------------------------------------------------------------------------

    /**
     * Publica un FundsReleasedEvent al topic [account.funds-released].
     * Confirma al payment-orchestrator que el rollback de fondos fue exitoso.
     *
     * @param event Evento con transactionId, accountId, amount y timestamp.
     */
    public void publishFundsReleased(FundsReleasedEvent event) {
        String key = event.getTransactionId();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_FUNDS_RELEASED, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[{}] Error al publicar FundsReleasedEvent | transactionId={} | error={}",
                        TOPIC_FUNDS_RELEASED, key, ex.getMessage(), ex);
            } else {
                log.info("[{}] FundsReleasedEvent publicado | transactionId={} | partition={} | offset={}",
                        TOPIC_FUNDS_RELEASED, key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}