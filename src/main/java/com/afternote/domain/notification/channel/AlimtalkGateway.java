package com.afternote.domain.notification.channel;

import java.util.Map;
/** Provider adapters must map approved templates and verify callback signatures before reporting acceptance.
 * No concrete provider is assumed: Kakao sharing is not the business-message API. */
public interface AlimtalkGateway {
    enum Outcome { ACCEPTED, RETRYABLE_REJECTION, REJECTED, UNKNOWN, UNAVAILABLE }
    record Result(Outcome outcome, String providerMessageId) { }
    Result send(String idempotencyKey, String phone, String templateKey, String body, Map<String, String> navigation);
}
