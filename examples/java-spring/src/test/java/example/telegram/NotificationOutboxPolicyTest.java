package example.telegram;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class NotificationOutboxPolicyTest {
    @Test
    void rejectsLeaseThatDoesNotOutlastTelegramRequestTimeout() {
        assertThrows(IllegalArgumentException.class, () -> policy(Duration.ofSeconds(10), Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> policy(Duration.ofSeconds(9), Duration.ofSeconds(10)));
    }

    @Test
    void acceptsLeaseLongerThanTelegramRequestTimeout() {
        assertDoesNotThrow(() -> policy(Duration.ofSeconds(11), Duration.ofSeconds(10)));
    }

    private NotificationOutboxPolicy policy(Duration leaseDuration, Duration requestTimeout) {
        return new NotificationOutboxPolicy(
                5, Duration.ofSeconds(2), Duration.ofMinutes(1), leaseDuration,
                Duration.ofHours(24), requestTimeout);
    }
}
