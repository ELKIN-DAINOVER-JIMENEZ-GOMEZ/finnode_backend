package com.finnode.payment.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.nio.charset.StandardCharsets;

/**
 * Serializa objetos a JSON UTF-8 para productores Kafka.
 */
public class JsonStringSerializer implements Serializer<Object> {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public byte[] serialize(String topic, Object data) {
        if (data == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(data);
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new SerializationException("Error serializing Kafka event to JSON", e);
        }
    }
}

