package com.afternote.domain.afternote.model;

import com.afternote.domain.afternote.dto.LeaveMessageBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveMessageListConverterTest {

    private final LeaveMessageListConverter converter = new LeaveMessageListConverter();

    @Test
    @DisplayName("JSON 배열 round-trip")
    void jsonRoundTrip() {
        List<LeaveMessageBlock> blocks = List.of(
                LeaveMessageBlock.builder().title("남긴말1").body("본문1").build(),
                LeaveMessageBlock.builder().title("남긴말2").body("본문2").build()
        );

        String db = converter.convertToDatabaseColumn(blocks);
        List<LeaveMessageBlock> restored = converter.convertToEntityAttribute(db);

        assertThat(db).startsWith("[");
        assertThat(restored).hasSize(2);
        assertThat(restored.get(0).getTitle()).isEqualTo("남긴말1");
        assertThat(restored.get(1).getBody()).isEqualTo("본문2");
    }

    @Test
    @DisplayName("레거시 plain string 은 단일 블록으로 wrap")
    void legacyString_WrapsAsSingleBlock() {
        List<LeaveMessageBlock> restored = converter.convertToEntityAttribute("예전 단일 문자열");

        assertThat(restored).hasSize(1);
        assertThat(restored.get(0).getTitle()).isEqualTo("");
        assertThat(restored.get(0).getBody()).isEqualTo("예전 단일 문자열");
    }

    @Test
    @DisplayName("null/blank 은 null")
    void nullAndBlank() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("  ")).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }
}
