package com.finnode.account.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración del productor Kafka para account-service.
 * Serializa los eventos de salida como JSON y los publica
 * a los tópicos correspondientes.
 * Eventos publicados:
 *   [account.funds-reserved]            → FundsReservedEvent
 *   [account.funds-reservation-failed]  → FundsReservationFailedEvent
 *   [account.funds-released]            → FundsReleasedEvent
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Fábrica de productores configurada con serialización JSON.
     * La clave del mensaje es un String y el valor es un objeto
     * serializado a JSON automáticamente.
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);

        // Agregar información de tipo en el header del mensaje para facilitar
        // la deserialización en los consumidores
        props.put(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, true);

        // Garantías de entrega: esperar confirmación de todos los réplicas
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // Reintentos en caso de fallo transitorio de red
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * KafkaTemplate es el componente principal para publicar mensajes.
     * Es inyectado en AccountEventPublisher para enviar eventos al broker.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}