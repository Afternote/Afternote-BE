package com.afternote.domain.afternote.model;

import com.afternote.domain.afternote.dto.LeaveMessageBlock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * leave_message TEXT 컬럼에 JSON 배열 문자열로 저장한다.
 * 레거시 plain string은 [{title:"", body:원문}] 으로 감싸 읽는다.
 */
@Converter
public class LeaveMessageListConverter implements AttributeConverter<List<LeaveMessageBlock>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<LeaveMessageBlock>> TYPE =
            new TypeReference<>() {
            };

    @Override
    public String convertToDatabaseColumn(List<LeaveMessageBlock> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("leaveMessage JSON 직렬화에 실패했습니다.", e);
        }
    }

    @Override
    public List<LeaveMessageBlock> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        String trimmed = dbData.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<LeaveMessageBlock> parsed = OBJECT_MAPPER.readValue(trimmed, TYPE);
                return parsed == null ? null : new ArrayList<>(parsed);
            } catch (JsonProcessingException e) {
                // JSON 배열처럼 보이지만 파싱 실패 시 레거시 문자열로 취급
                return wrapLegacy(dbData);
            }
        }

        return wrapLegacy(dbData);
    }

    private static List<LeaveMessageBlock> wrapLegacy(String raw) {
        List<LeaveMessageBlock> blocks = new ArrayList<>(1);
        blocks.add(LeaveMessageBlock.builder()
                .title("")
                .body(raw)
                .build());
        return blocks;
    }
}
