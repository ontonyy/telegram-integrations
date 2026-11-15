package example.telegram;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationOutboxDispatcher {
    private final NotificationOutbox outbox;
    private final NotificationSender sender;
    private final NotificationOutboxPolicy policy;
    private final boolean sendsEnabled;

    public NotificationOutboxDispatcher(
            NotificationOutbox outbox,
            NotificationSender sender,
            NotificationOutboxPolicy policy,
            @Value("${telegram.send-enabled:false}") boolean sendsEnabled) {
        this.outbox = outbox;
        this.sender = sender;
        this.policy = policy;
        this.sendsEnabled = sendsEnabled;
    }

    /** Process at most one row. A scheduler should call this with a real clock in a deployment adapter. */
    public boolean dispatchOne(Instant now) {
        if (!sendsEnabled) {
            return false;
        }
        outbox.purgeTerminalBefore(now.minus(policy.terminalRetention()));
        Optional<OutboxMessage> claimed = outbox.claimNext(now, policy.leaseDuration());
        if (claimed.isEmpty()) {
            return false;
        }
        OutboxMessage message = claimed.orElseThrow();
        NotificationDeliveryResult result;
        try {
            result = sender.send(message.chatId(), message.text());
        } catch (RuntimeException ambiguousTransportFailure) {
            result = NotificationDeliveryResult.retryable(Duration.ZERO);
        }

        switch (result.kind()) {
            case SENT -> outbox.markSent(message.eventId(), message.attempts());
            case PERMANENT_FAILURE -> outbox.markFailed(message.eventId(), message.attempts(), "permanent_failure");
            case RETRYABLE_FAILURE -> {
                if (message.attempts() >= policy.maxAttempts()) {
                    outbox.markFailed(message.eventId(), message.attempts(), "attempt_limit");
                } else {
                    Duration delay = policy.retryDelay(message.attempts(), result.retryAfter());
                    Instant retryAt = now.plus(delay);
                    if (!retryAt.isBefore(message.expiresAt())) {
                        outbox.markExpired(message.eventId(), message.attempts());
                    } else {
                        outbox.markRetry(message.eventId(), message.attempts(), retryAt, "transient_failure");
                    }
                }
            }
        }
        return true;
    }
}
