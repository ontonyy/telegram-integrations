package example.telegram;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationOutboxPolicy {
    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final Duration leaseDuration;
    private final Duration terminalRetention;

    public NotificationOutboxPolicy(
            @Value("${telegram.outbox.max-attempts:5}") int maxAttempts,
            @Value("${telegram.outbox.base-delay:PT2S}") Duration baseDelay,
            @Value("${telegram.outbox.max-delay:PT1M}") Duration maxDelay,
            @Value("${telegram.outbox.lease-duration:PT30S}") Duration leaseDuration,
            @Value("${telegram.outbox.terminal-retention:PT24H}") Duration terminalRetention,
            @Value("${telegram.request-timeout:PT10S}") Duration telegramRequestTimeout) {
        if (maxAttempts < 1 || baseDelay.isNegative() || maxDelay.compareTo(baseDelay) < 0
                || leaseDuration.isZero() || leaseDuration.isNegative()
                || terminalRetention.isNegative()
                || telegramRequestTimeout.isZero() || telegramRequestTimeout.isNegative()) {
            throw new IllegalArgumentException("Outbox retry and lease settings are invalid");
        }
        if (leaseDuration.compareTo(telegramRequestTimeout) <= 0) {
            throw new IllegalArgumentException("Outbox lease duration must exceed the Telegram request timeout");
        }
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.leaseDuration = leaseDuration;
        this.terminalRetention = terminalRetention;
    }

    public int maxAttempts() { return maxAttempts; }

    public Duration leaseDuration() { return leaseDuration; }

    public Duration terminalRetention() { return terminalRetention; }

    public Duration retryDelay(int attempt, Duration providerRetryAfter) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 30);
        Duration exponential = baseDelay.multipliedBy(multiplier);
        Duration bounded = exponential.compareTo(maxDelay) > 0 ? maxDelay : exponential;
        if (providerRetryAfter == null || providerRetryAfter.isNegative()) {
            return bounded;
        }
        return providerRetryAfter.compareTo(bounded) > 0 ? providerRetryAfter : bounded;
    }
}
