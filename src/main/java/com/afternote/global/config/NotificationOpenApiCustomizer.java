package com.afternote.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;

/** Prevent inferred success DTOs from being advertised as notification error response bodies. */
public class NotificationOpenApiCustomizer implements OpenApiCustomizer {
    private static final Set<String> PATHS = Set.of("/api/v1/users/push-tokens", "/api/v1/users/push-installations",
            "/api/v1/users/notifications", "/api/v1/users/notifications/{id}/read", "/api/v1/admin/push-tokens/test");

    @Override
    public void customise(OpenAPI api) {
        if (api.getPaths() == null || api.getComponents() == null) return;
        api.getComponents().addSchemas("NotificationErrorResponse", envelope("오류 응답. data는 항상 null"));
        api.getComponents().addSchemas("NotificationEmptyResponse", envelope("성공 응답. status/code=200, data=null"));
        api.getPaths().forEach((path, item) -> {
            if (!PATHS.contains(path) && !path.startsWith("/api/v1/receiver-invitations")
                    && !path.startsWith("/api/v1/users/me/notification-preferences") && !path.startsWith("/api/v1/notification-links")
                    && !path.equals("/api/v1/time-letters/{id}/delivery-status")
                    && !path.equals("/api/v1/users/notifications/{id}/target") && !path.equals("/api/v1/time-letters/{id}/retry-delivery")) return;
            item.readOperationsMap().forEach((method, operation) -> {
                if (operation.getResponses() == null) return;
                operation.getResponses().forEach((code, response) -> {
                    if (code.matches("[45]\\d\\d")) setSchema(response, "NotificationErrorResponse");
                    if (code.equals("200") && (path.equals("/api/v1/users/notifications/{id}/read")
                            || path.equals("/api/v1/receiver-invitations/{token}/decline")
                            || (path.equals("/api/v1/users/me/notification-preferences/delegate") && !method.name().equals("GET"))
                            || (path.startsWith("/api/v1/users/me/notification-preferences/anniversaries/") && method.name().equals("DELETE"))
                            || (path.equals("/api/v1/users/push-tokens") && method.name().equals("DELETE")))) {
                        setSchema(response, "NotificationEmptyResponse");
                    }
                });
            });
        });
    }
    private static Schema<?> envelope(String description) {
        return new ObjectSchema().description(description)
                .addProperty("status", new IntegerSchema().description("HTTP 상태 코드"))
                .addProperty("code", new IntegerSchema().description("서버 응답 코드"))
                .addProperty("message", new StringSchema().description("응답 메시지"))
                .addProperty("data", new Schema<>().nullable(true).description("항상 null"))
                .required(List.of("status", "code", "message", "data"));
    }
    private static void setSchema(ApiResponse response, String schema) {
        response.setContent(new Content().addMediaType("application/json", new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/"+schema))));
    }
}
