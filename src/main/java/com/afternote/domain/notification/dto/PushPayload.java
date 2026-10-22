package com.afternote.domain.notification.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Versioned, data-only FCM contract. Navigation values require client adoption. */
public final class PushPayload {
    private static final Set<String> DESTINATIONS = Set.of(
            "HOME", "AGENT_CONFIRMATION", "INVITATION", "RECORD_LIST", "RECORD_DETAIL", "AGENT_SETTINGS", "RETENTION_CONFIRMATION",
            "DAILY_QUESTION", "WEEKLY_REPORT", "EASY_QUESTIONS", "TIME_LETTER_COMPOSE",
            "RECEIVER_DETAIL", "INVITATION_DETAIL", "RECEIVER_HOME", "TIME_LETTER_DETAIL", "PLAN", "SUBSCRIPTION_MANAGEMENT");
    private static final Map<String, String> REQUIRED_IDS = Map.of(
            "RECORD_DETAIL", "recordId", "DAILY_QUESTION", "questionId",
            "RECEIVER_DETAIL", "receiverId", "TIME_LETTER_DETAIL", "timeLetterId",
            "TIME_LETTER_COMPOSE", "receiverId", "INVITATION_DETAIL", "invitationId");

    private PushPayload() { }

    public static Map<String, String> create(String title, String body, Map<String, String> extras) {
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            throw new IllegalArgumentException("Push title and body are required");
        }
        Map<String, String> payload = new HashMap<>();
        if (extras != null) {
            extras.forEach((key, value) -> {
                if (key == null || key.isBlank() || value == null || key.equals("from")
                        || key.equals("message_type") || key.startsWith("google.") || key.startsWith("gcm.")
                        || Set.of("title", "body", "schemaVersion").contains(key)) {
                    throw new IllegalArgumentException("Invalid or reserved push data key");
                }
                payload.put(key, value);
            });
        }
        if (payload.containsKey("action") && !Set.of("VIEW", "EDIT", "REINVITE", "DELETE", "RETRY",
                "EDIT_CONTACT", "CANCEL_PROCEDURE", "WEEKLY_PROGRESS").contains(payload.get("action"))) {
            throw new IllegalArgumentException("Unsupported push action");
        }
        String destination = payload.getOrDefault("destination", "HOME");
        if (!DESTINATIONS.contains(destination)) {
            throw new IllegalArgumentException("Unsupported push destination");
        }
        String requiredId = REQUIRED_IDS.get(destination);
        if (requiredId != null) {
            try {
                if (Long.parseLong(payload.getOrDefault(requiredId, "")) <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Push destination requires a positive " + requiredId);
            }
        }
        if (Set.of("AGENT_CONFIRMATION", "INVITATION").contains(destination)
                && payload.getOrDefault("linkId", "").isBlank()) {
            throw new IllegalArgumentException("Web destination requires an opaque linkId");
        }
        payload.put("destination", destination);
        payload.put("schemaVersion", "1");
        payload.put("title", title);
        payload.put("body", body);
        return Map.copyOf(payload);
    }
}
