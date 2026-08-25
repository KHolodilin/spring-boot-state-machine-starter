package com.kholodilin.statemachine.persistence;

import java.util.Map;

import com.kholodilin.statemachine.exception.PayloadDeserializationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonMapsTest {

    private final JsonMaps jsonMaps = new JsonMaps(JsonMapper.builder().build());

    @Test
    void writeReadMapAndPayload() {
        assertThat(jsonMaps.write(null)).isNull();
        assertThat(jsonMaps.readMap(null)).isEmpty();
        assertThat(jsonMaps.readMap("  ")).isEmpty();
        assertThat(jsonMaps.read(null, String.class)).isNull();
        assertThat(jsonMaps.read("{}", Void.class)).isNull();
        assertThat(jsonMaps.readMap(jsonMaps.write(Map.of("k", "v")))).containsEntry("k", "v");
        assertThatThrownBy(() -> jsonMaps.readMap("{not-json")).isInstanceOf(PayloadDeserializationException.class);
        assertThatThrownBy(() -> jsonMaps.read("{not-json", String.class))
                .isInstanceOf(PayloadDeserializationException.class);
        assertThatThrownBy(() -> jsonMaps.write(new ThrowingBean()))
                .isInstanceOf(PayloadDeserializationException.class);
    }

    public static final class ThrowingBean {
        public String getValue() {
            throw new IllegalStateException("cannot serialize");
        }
    }
}
