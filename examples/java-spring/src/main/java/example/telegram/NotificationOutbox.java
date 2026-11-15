package example.telegram;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface NotificationOutbox {
    boolean enqueue(String eventId, String chatId, String text, Instant now, Instant expiresAt);

    Optional<OutboxMessage> claimNext(Instant now, Duration leaseDuration);

    void markSent(String eventId, int claimedAttempt);

    void markRetry(String eventId, int claimedAttempt, Instant nextAttemptAt, String failureKind);

    void markFailed(String eventId, int claimedAttempt, String failureKind);

    void markExpired(String eventId, int claimedAttempt);

    int expireDue(Instant now);

    int purgeTerminalBefore(Instant cutoff);
}
