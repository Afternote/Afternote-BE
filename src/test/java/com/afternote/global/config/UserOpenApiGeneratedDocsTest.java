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
}
