package com.afternote.domain.notification.dto;
/** Provider acceptance is separate from device display or reading. */
public record PushDeliveryResult(int sentCount, int failedCount, boolean permissionDenied, boolean configured, Failure failure) {
    public enum Failure { NONE, NO_TOKEN, PERMISSION_DENIED, UNREGISTERED, INVALID_MESSAGE, CONFIGURATION, TEMPORARY_REJECTION, UNKNOWN }
    public PushDeliveryResult(int sent, int failed, boolean denied, boolean configured) {
        this(sent, failed, denied, configured, sent > 0 ? Failure.NONE : denied ? Failure.PERMISSION_DENIED
                : !configured ? Failure.CONFIGURATION : failed > 0 ? Failure.UNKNOWN : Failure.NO_TOKEN);
    }
    public boolean delivered() { return sentCount > 0; }
    public boolean allowsAlimtalkFallback() {
        return permissionDenied || (configured && failure != Failure.INVALID_MESSAGE && failure != Failure.CONFIGURATION && failure != Failure.UNKNOWN);
    }
}
