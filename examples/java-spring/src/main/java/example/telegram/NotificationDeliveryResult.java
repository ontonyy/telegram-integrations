package example.telegram;

import java.time.Duration;

public record NotificationDeliveryResult(Kind kind, Duration retryAfter) {
    public enum Kind { SENT, RETRYABLE_FAILURE, PERMANENT_FAILURE }

    public static NotificationDeliveryResult sent() {
        return new NotificationDeliveryResult(Kind.SENT, Duration.ZERO);
    }

    public static NotificationDeliveryResult retryable(Duration retryAfter) {
        return new NotificationDeliveryResult(Kind.RETRYABLE_FAILURE, retryAfter);
    }

    public static NotificationDeliveryResult permanent() {
        return new NotificationDeliveryResult(Kind.PERMANENT_FAILURE, Duration.ZERO);
    }
}
