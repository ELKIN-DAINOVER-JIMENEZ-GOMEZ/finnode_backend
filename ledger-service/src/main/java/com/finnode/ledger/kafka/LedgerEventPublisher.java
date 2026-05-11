package com.finnode.ledger.kafka;

import com.finnode.ledger.event.LedgerEntriesRecordedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publisher de eventos Kafka del ledger-service.
 *
 * Publica al topic [ledger.entries-recorded] una vez que los asientos
 * contables fueron persistidos exitosamente en la base de datos.
 *
 * El payment-orchestrator escucha este topic para saber que el paso
 * contable del Patrón Saga fue completado y puede avanzar al siguiente.
 *
 * ENVÍO ASÍNCRONO CON CALLBACK:
 * Se usa KafkaTemplate.send() que retorna un CompletableFuture.
 * Esto no bloquea el hilo mientras Kafka confirma la recepción del mensaje.
 * El callback registra si el envío fue exitoso o falló, sin lanzar excepción
 * al servicio que lo llama — el flujo principal ya completó su trabajo
 * (persistir los asientos) y la publicación es el paso final de notificación.
 *
 * NOTA SOBRE GARANTÍAS:
 * Si este publisher falla después de que los asientos ya fueron persistidos,
 * el payment-orchestrator no recibirá confirmación y activará su timeout Saga,
 * publicando un PaymentReversedEvent. LedgerService lo manejará con idempotencia:
 * detectará que ya existen asientos para esa transacción y no los duplicará.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerEventPublisher {

    private final KafkaTemplate<String, LedgerEntriesRecordedEvent> kafkaTemplate;

    @Value("${kafka.topics.ledger-entries-recorded:ledger.entries-recorded}")
    private String ledgerEntriesRecordedTopic;

    /**
     * Publica el evento de confirmación de asientos contables registrados.
     *
     * Se llama desde LedgerService al finalizar exitosamente:
     *   - recordEntries()         → reversalConfirmed = false
     *   - recordReversalEntries() → reversalConfirmed = true
     *
     * La clave del mensaje Kafka es el transactionId en String.
     * Esto garantiza que todos los eventos de una misma transacción
     * vayan a la misma partición y sean procesados en orden por el consumer.
     *
     * @param event evento con los IDs de los asientos creados y el flag de reversión
     */
    public void publishLedgerEntriesRecorded(LedgerEntriesRecordedEvent event) {
        log.info("[KAFKA] Publicando ledger.entries-recorded | transactionId={} | reversalConfirmed={}",
                event.transactionId(), event.reversalConfirmed());

        CompletableFuture<SendResult<String, LedgerEntriesRecordedEvent>> future =
                kafkaTemplate.send(
                        ledgerEntriesRecordedTopic,
                        event.transactionId().toString(), // clave de particionamiento
                        event
                );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("[KAFKA] Evento publicado exitosamente | transactionId={} | partition={} | offset={}",
                        event.transactionId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("[KAFKA] Error al publicar ledger.entries-recorded | transactionId={} | error={}",
                        event.transactionId(), ex.getMessage(), ex);
                // El payment-orchestrator activará su timeout Saga y publicará
                // un PaymentReversedEvent. LedgerService lo manejará con idempotencia.
            }
        });
    }
}