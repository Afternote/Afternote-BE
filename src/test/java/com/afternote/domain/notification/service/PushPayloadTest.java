package com.afternote.domain.notification.service;
import com.afternote.domain.notification.dto.PushPayload;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class PushPayloadTest {
    @Test void detailNeedsConcreteId() {
        assertThatThrownBy(() -> PushPayload.create("a", "b", Map.of("destination", "RECORD_DETAIL")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PushPayload.create("a", "b", Map.of("destination", "RECORD_DETAIL", "recordId", "42")))
                .containsEntry("recordId", "42").containsEntry("schemaVersion", "1");
    }
    @Test void reservedContentCannotBeOverwritten() {
        assertThatThrownBy(() -> PushPayload.create("a", "b", Map.of("title", "spoof")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushPayload.create("a", "b", Map.of("google.foo", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
