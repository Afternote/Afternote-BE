package com.afternote.global.config;

import com.afternote.global.exception.ErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        ErrorCode rateLimit = ErrorCode.RATE_LIMIT_EXCEEDED;
        Schema<?> dataSchema = new Schema<>().nullable(true);
        Schema<?> rateLimitBody = new ObjectSchema().properties(Map.of(
                "status", new IntegerSchema().example(rateLimit.getHttpStatus().value()),
                "code", new IntegerSchema().example(rateLimit.getCode()),
                "message", new StringSchema().example(rateLimit.getMessage()),
                "data", dataSchema
        ));

        return new OpenAPI()
                .servers(List.of(
                        new Server().url("https://afternote.kro.kr").description("Production Server"),
                        new Server().url("http://localhost:8080").description("Local Server")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearer-key",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addResponses("TooManyRequests",
                                new ApiResponse()
                                        .description("요청 한도 초과 (nginx rate limit). 공통 JSON 형식.")
                                        .content(new Content()
                                                .addMediaType("application/json",
                                                        new MediaType().schema(rateLimitBody)))))
                .addSecurityItem(new SecurityRequirement().addList("bearer-key"))
                .info(apiInfo());
    }

    private Info apiInfo() {
        return new Info()
                .title("AfterNote API 명세서")
                .description("AfterNote API 문서입니다. rate limit(429) 응답은 nginx에서 공통 JSON으로 반환합니다. "
                        + "스키마: Components → Responses → TooManyRequests.")
                .version("1.0.0");
    }
}
