package com.afternote.global.config;

import com.afternote.domain.notification.service.UserPushTokenService;
import com.afternote.domain.user.controller.UserController;
import com.afternote.domain.user.service.UserService;
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

@WebMvcTest(
        controllers = UserController.class,
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
class UserOpenApiGeneratedDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private UserPushTokenService userPushTokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("생성 OpenAPI의 DELETE /users/me 는 S3 삭제와 401을 남긴다")
    void generatedOpenApi_DeleteUsersMeDocumentsS3Purge() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode docs = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        JsonNode deleteMe = docs.at("/paths/~1api~1v1~1users~1me/delete");

        assertThat(deleteMe.isMissingNode()).isFalse();
        assertThat(deleteMe.path("summary").asText()).contains("회원 탈퇴");
        assertThat(deleteMe.path("description").asText())
                .contains("S3")
                .contains("30일");
        assertThat(deleteMe.at("/responses/200").isMissingNode()).isFalse();
        assertThat(deleteMe.at("/responses/401").isMissingNode()).isFalse();
        assertThat(docs.at("/security/0/bearer-key").isArray()).isTrue();
    }

    @Test
    @DisplayName("수신자 메시지 수정 스키마는 생략·null 비우기를 구분한다")
    void generatedOpenApi_ReceiverMessagePatchDocumentsClear() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode docs = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        JsonNode schema = docs.at("/components/schemas/UserUpdateReceiverMessageRequest");
        JsonNode message = schema.at("/properties/message");

        assertThat(schema.at("/properties/messageSpecified").isMissingNode()).isTrue();
        assertThat(message.path("nullable").asBoolean()).isTrue();
        assertThat(textValues(schema.path("required"))).doesNotContain("message");
        assertThat(message.path("description").asText()).contains("생략").contains("null");
        assertThat(docs.at("/paths/~1api~1v1~1users~1receivers~1{receiverId}~1message/patch/description").asText())
                .contains("null");
    }

    @Test
    @DisplayName("프로필 수정 스키마는 연락처·사진의 생략과 비우기를 구분한다")
    void generatedOpenApi_ProfilePatchDocumentsClear() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode docs = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        JsonNode schema = docs.at("/components/schemas/UserUpdateProfileRequest");

        assertThat(schema.at("/properties/phoneSpecified").isMissingNode()).isTrue();
        assertThat(schema.at("/properties/phone/nullable").asBoolean()).isTrue();
        assertThat(schema.at("/properties/profileImageUrl/nullable").asBoolean()).isTrue();
        assertThat(schema.at("/properties/phone/description").asText()).contains("생략").contains("null");
        assertThat(textValues(schema.path("required"))).doesNotContain("phone", "profileImageUrl");
    }

    private static java.util.List<String> textValues(JsonNode array) {
        java.util.List<String> values = new java.util.ArrayList<>();
        for (JsonNode item : array) {
            values.add(item.asText());
        }
        return values;
    }
}
