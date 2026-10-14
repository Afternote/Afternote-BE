package com.afternote.domain.notification.dto;
import com.afternote.domain.notification.model.NotificationPermission;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record PushInstallationRequest(@NotNull UUID installationId, @NotNull NotificationPermission permission) { }
