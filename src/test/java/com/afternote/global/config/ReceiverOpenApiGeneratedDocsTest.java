package com.afternote.global.config;

import com.afternote.domain.receiver.controller.ReceiverAuthController;
import com.afternote.domain.receiver.dto.ReceivedRecordBoxResponse;
import com.afternote.domain.receiver.service.ReceiverAuthService;
import com.afternote.global.jwt.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #269: 수신자 응답의 required·nullable이 생성 OpenAPI에 실제로 남는지 고정한다.
 */
@WebMvcTest(
        controllers = ReceiverAuthController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({SwaggerConfig.class})
@ImportAutoConfiguration({
        org.springdoc.core.configuration.SpringDocConfiguration.class,
        org.springdoc.core.properties.SpringDocConfigProperties.class,
        org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration.class
})
class ReceiverOpenApiGeneratedDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReceiverAuthService receiverAuthService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("생성 OpenAPI에서 ReceivedRecordBoxResponse는 항상 필드 required, 조건부 필드는 nullable이다")
    void generatedOpenApi_ReceivedRecordBoxRequiredAndNullable() throws Exception {
        JsonNode schemas = fetchSchemas();
        JsonNode schema = schemas.get("ReceivedRecordBoxResponse");
        assertThat(schema).isNotNull();

        assertThat(textValues(schema.path("required"))).contains(
                "receiverId", "accessCode", "senderName", "receiverName", "recordStatus", "viewStatus"
        );
        assertThat(textValues(schema.path("required")))
                .doesNotContain("relation", "verificationStatus", "requestedAt", "approvedAt");

        assertThat(textValues(schema.at("/properties/recordStatus/enum")))
                .containsExactlyInAnyOrder("STORED", "EMPTY")
                .doesNotContain("DELETED");

        assertThat(schema.at("/properties/relation/nullable").asBoolean()).isTrue();
        assertThat(schema.at("/properties/relation/description").asText()).contains("null");
        assertThat(schema.at("/properties/verificationStatus/nullable").asBoolean()).isTrue();
        assertThat(schema.at("/properties/verificationStatus/description").asText()).contains("열람 신청이 없으면 null");

        JsonNode requestedAt = schema.at("/properties/requestedAt");
        assertThat(requestedAt.path("nullable").asBoolean()).isTrue();
        assertThat(requestedAt.path("format").asText())
                .isEqualTo(ReceivedRecordBoxResponse.LOCAL_DATE_TIME_FORMAT);
        assertThat(requestedAt.path("description").asText()).contains("오프셋 없는");

        JsonNode approvedAt = schema.at("/properties/approvedAt");
        assertThat(approvedAt.path("nullable").asBoolean()).isTrue();
        assertThat(approvedAt.path("format").asText()).isNotEqualTo("date-time");
        assertThat(approvedAt.path("description").asText()).contains("APPROVED");
    }

    @Test
    @DisplayName("생성 OpenAPI에서 수신 타임레터·인증·메시지·상세 playlist nullable이 산출물에 남는다")
    void generatedOpenApi_OtherReceiverResponsesKeepNullable() throws Exception {
        JsonNode schemas = fetchSchemas();

        JsonNode timeLetter = schemas.get("ReceivedTimeLetterResponse");
        assertThat(textValues(timeLetter.path("required")))
                .contains("id", "timeLetterReceiverId", "blocks", "status", "deliveredAt");
        assertThat(timeLetter.at("/properties/title/nullable").asBoolean()).isTrue();
        assertThat(timeLetter.at("/properties/sendAt/nullable").asBoolean()).isTrue();
        assertThat(timeLetter.at("/properties/sendAt/format").asText())
                .isEqualTo(ReceivedRecordBoxResponse.LOCAL_DATE_TIME_FORMAT);

        JsonNode verify = schemas.get("ReceiverAuthVerifyResponse");
        assertThat(textValues(verify.path("required"))).contains("receiverId", "receiverName", "senderName");
        assertThat(verify.at("/properties/relation/nullable").asBoolean()).isTrue();

        JsonNode message = schemas.get("ReceiverMessageResponse");
        assertThat(textValues(message.path("required"))).contains("senderName", "createdAt");
        assertThat(message.at("/properties/message/nullable").asBoolean()).isTrue();

        JsonNode verification = schemas.get("DeliveryVerificationResponse");
        assertThat(textValues(verification.path("required"))).contains("id", "status", "createdAt");
        assertThat(verification.at("/properties/deathCertificateUrl/nullable").asBoolean()).isTrue();
        assertThat(verification.at("/properties/adminNote/nullable").asBoolean()).isTrue();

        JsonNode playlist = schemas.at("/ReceivedAfternoteDetailResponse/properties/playlist");
        assertThat(playlist.has("$ref"))
                .as("OpenAPI 3.0 $ref cannot keep sibling nullable/description")
                .isFalse();
        assertThat(playlist.path("nullable").asBoolean()).isTrue();
        assertThat(playlist.path("description").asText()).contains("PLAYLIST");
        assertThat(playlist.has("allOf")).isTrue();
    }

    private JsonNode fetchSchemas() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).at("/components/schemas");
    }

    private static java.util.List<String> textValues(JsonNode array) {
        java.util.List<String> values = new java.util.ArrayList<>();
        for (JsonNode item : array) {
            values.add(item.asText());
        }
        return values;
    }
}
