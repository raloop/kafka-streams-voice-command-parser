package eu.raloop.kafka.streams.voice.serdes;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;

public class JsonSerde<T> implements Serde<T> {


    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Class<T> type;

    public JsonSerde(Class<T> type) {
        this.type = type;
    }

    @Override
    public Serializer<T> serializer() {
        return (topic, data) -> serialize(data);
    }

    @SneakyThrows
    private byte[] serialize(T data) {
        return OBJECT_MAPPER.writeValueAsBytes(data);
    }

    @Override
    public Deserializer<T> deserializer() {
        return (topic, data) -> deserialize(data);
    }

    @SneakyThrows
    private T deserialize(byte[] data) {
        return OBJECT_MAPPER.readValue(data, type);
    }
}
