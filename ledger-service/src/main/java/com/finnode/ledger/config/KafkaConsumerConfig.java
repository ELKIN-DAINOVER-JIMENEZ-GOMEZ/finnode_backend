package com.finnode.ledger.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración del consumer de Kafka para el ledger-service.
 *
 * Define tres comportamientos críticos para un sistema financiero:
 *
 * 1. ACKNOWLEDGMENT MANUAL (AckMode.MANUAL)
 *    El offset en Kafka solo avanza cuando el código llama ack.acknowledge().
 *    Si LedgerService lanza una excepción, el offset NO avanza y Kafka
 *    reentrega el mensaje. Ningún movimiento financiero se pierde.
 *
 * 2. REINTENTOS CON BACKOFF FIJO
 *    Si el procesamiento falla, Kafka reintenta el mensaje hasta 3 veces
 *    con una pausa de 3 segundos entre intentos. Esto da tiempo a que
 *    errores transitorios (base de datos lenta, red inestable) se resuelvan
 *    solos sin intervención manual.
 *
 * 3. DEAD LETTER QUEUE (DLQ)
 *    Si un mensaje falla los 3 reintentos, se mueve automáticamente al topic
 *    [payment.completed.DLT] o [payment.reversed.DLT] según corresponda.
 *    El mensaje corrupto o irrecuperable no bloquea al resto — el consumer
 *    sigue procesando los demás mensajes de la partición normalmente.
 *    El equipo puede inspeccionar la DLQ manualmente para diagnosticar el error.
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:ledger-service-group}")
    private String groupId;

    // Número máximo de reintentos antes de enviar a DLQ
    private static final long MAX_ATTEMPTS  = 3L;

    // Pausa entre reintentos en milisegundos
    private static final long BACKOFF_MS    = 3_000L;

    // -------------------------------------------------------------------------
    // CONSUMER FACTORY — PaymentCompletedEvent
    // -------------------------------------------------------------------------

    /**
     * Factory para deserializar mensajes del topic [payment.completed].
     * Configura el JsonDeserializer para convertir el JSON de Kafka
     * directamente a PaymentCompletedEvent.
     */
    @Bean
    public ConsumerFactory<String, String> paymentCompletedConsumerFactory() {
        // Usamos StringDeserializer y delegamos la conversión JSON → POJO al
        // RecordMessageConverter configurado en kafkaListenerContainerFactory.
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                new StringDeserializer()
        );
    }

    // -------------------------------------------------------------------------
    // CONSUMER FACTORY — PaymentReversedEvent
    // -------------------------------------------------------------------------

    /**
     * Factory para deserializar mensajes del topic [payment.reversed].
     * Configura el JsonDeserializer para convertir el JSON de Kafka
     * directamente a PaymentReversedEvent.
     */
    @Bean
    public ConsumerFactory<String, String> paymentReversedConsumerFactory() {
        // Usamos StringDeserializer y delegamos la conversión JSON → POJO al
        // RecordMessageConverter configurado en kafkaListenerContainerFactory.
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                new StringDeserializer()
        );
    }

    // -------------------------------------------------------------------------
    // LISTENER CONTAINER FACTORY
    // -------------------------------------------------------------------------

    /**
     * Factory principal que usan los @KafkaListener del LedgerEventConsumer.
     *
     * Configura:
     *   - AckMode.MANUAL → el offset solo avanza con ack.acknowledge()
     *   - DefaultErrorHandler con FixedBackOff → 3 reintentos cada 3 segundos
     *   - DeadLetterPublishingRecoverer → DLQ automática tras agotar reintentos
     *
     * Todos los @KafkaListener que usen containerFactory = "kafkaListenerContainerFactory"
     * heredan esta configuración automáticamente.
     *
     * @param kafkaTemplate usado por DeadLetterPublishingRecoverer para publicar en DLQ
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        // Usamos la factory de PaymentCompletedEvent como base
        // Los dos tipos de eventos son compatibles con la configuración base
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                new StringDeserializer()
        ));

        // Convertimos JSON (String) a objetos POJO usando Jackson.
        factory.setRecordMessageConverter(
                new StringJacksonJsonMessageConverter(JsonMapper.builder().build())
        );

        // ACKNOWLEDGMENT MANUAL
        // El offset solo avanza cuando LedgerEventConsumer llama ack.acknowledge()
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // ERROR HANDLER CON REINTENTOS Y DLQ
        // FixedBackOff(intervalo_ms, max_intentos)
        // → reintenta hasta MAX_ATTEMPTS veces con BACKOFF_MS de pausa entre intentos
        // → tras agotar los reintentos, DeadLetterPublishingRecoverer publica en DLQ
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(BACKOFF_MS, MAX_ATTEMPTS)
        );

        // No reintentamos LedgerImbalanceException — es un error de datos permanente.
        // Reintentarla 3 veces no cambiará el resultado; va directo a DLQ.
        errorHandler.addNotRetryableExceptions(
                com.finnode.ledger.exception.LedgerImbalanceException.class
        );

        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    // -------------------------------------------------------------------------
    // PROPIEDADES BASE DEL CONSUMER
    // -------------------------------------------------------------------------

    /**
     * Propiedades comunes compartidas por todos los ConsumerFactory.
     *
     * AUTO_OFFSET_RESET = earliest:
     * Si el consumer group no tiene offset registrado (primera vez que arranca
     * o el offset expiró), empieza a leer desde el mensaje más antiguo disponible.
     * En un sistema financiero es preferible reprocesar mensajes viejos que
     * perder mensajes nuevos.
     *
     * ENABLE_AUTO_COMMIT = false:
     * Deshabilita el commit automático de offsets. Funciona en conjunto con
     * AckMode.MANUAL — el offset solo se confirma cuando el código lo ordena.
     */
    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,           groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }
}