package com.finnode.account.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración del consumidor Kafka para account-service.
 * - Consumer group: account-service-group
 * - Deserialización JSON de los eventos entrantes
 * - Política de reintentos con FixedBackOff (3 intentos, 1 segundo entre cada uno)
 * - Mensajes que fallan repetidamente son enviados a una Dead Letter Queue (DLQ)
 * Tópicos escuchados:
 *   [user.registered]  → para crear cuentas bancarias automáticamente
 *   [payment.reverse]  → para ejecutar rollbacks del Patrón Saga
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Fábrica de consumidores configurada para deserializar
     * los eventos entrantes como JSON.
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        JacksonJsonDeserializer<Object> deserializer = new JacksonJsonDeserializer<>();
        // Permitir clases del paquete com.finnode para deserialización segura
        deserializer.addTrustedPackages("com.finnode.*");
        deserializer.setUseTypeMapperForKey(false);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "account-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    /**
     * Fábrica de contenedores de listeners con manejo de errores.
     * Política de reintentos: 3 intentos con 1 segundo de espera entre cada uno.
     * Si los 3 intentos fallan, el mensaje se descarta (o se envía a DLQ si está configurada).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // Manejo de errores: 3 reintentos con 1 segundo de pausa entre cada uno
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                new FixedBackOff(1000L, 3L) // intervalo: 1000ms, máximo de intentos: 3
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
