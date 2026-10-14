package com.afternote.domain.notification.dto;
import com.afternote.domain.notification.model.NotificationPermission;
public record PushInstallationResponse(String installationId, NotificationPermission permission, boolean showPermissionBanner) { }
