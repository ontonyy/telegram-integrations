package example.telegram;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationOutbox implements NotificationOutbox {
    private final JdbcTemplate jdbc;

    public JdbcNotificationOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean enqueue(String eventId, String chatId, String text, Instant now, Instant expiresAt) {
        if (eventId == null || eventId.isBlank() || eventId.length() > 128) {
            throw new IllegalArgumentException("A bounded stable event ID is required");
        }
        if (chatId == null || chatId.isBlank() || chatId.length() > 64) {
            throw new IllegalArgumentException("A bounded destination ID is required");
        }
        if (text == null || text.isBlank() || text.length() > 4000) {
            throw new IllegalArgumentException("Notification text must contain 1 to 4000 characters");
        }
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Notification expiry must be after creation");
        }
        try {
            return jdbc.update("""
                    INSERT INTO telegram_notification_outbox
                        (event_id, chat_id, message_text, state, attempts, created_at,
                         next_attempt_at, expires_at)
                    VALUES (?, ?, ?, 'PENDING', 0, ?, ?, ?)
                    """, eventId, chatId, text, timestamp(now), timestamp(now), timestamp(expiresAt)) == 1;
        } catch (DuplicateKeyException alreadyEnqueued) {
            return false;
        }
    }

    @Override
    public Optional<OutboxMessage> claimNext(Instant now, Duration leaseDuration) {
        expireDue(now);
        Timestamp nowValue = timestamp(now);
        List<String> candidates = jdbc.queryForList("""
                SELECT event_id FROM telegram_notification_outbox
                WHERE expires_at > ? AND (
                    (state IN ('PENDING', 'RETRY') AND next_attempt_at <= ?)
                    OR (state = 'SENDING' AND lease_until <= ?)
                )
                ORDER BY created_at, event_id
                LIMIT 10
                """, String.class, nowValue, nowValue, nowValue);

        for (String eventId : candidates) {
            int claimed = jdbc.update("""
                    UPDATE telegram_notification_outbox
                    SET state = 'SENDING', attempts = attempts + 1, lease_until = ?, last_failure_kind = NULL
                    WHERE event_id = ? AND expires_at > ? AND (
                        (state IN ('PENDING', 'RETRY') AND next_attempt_at <= ?)
                        OR (state = 'SENDING' AND lease_until <= ?)
                    )
                    """, timestamp(now.plus(leaseDuration)), eventId, nowValue, nowValue, nowValue);
            if (claimed == 1) {
                OutboxMessage message = jdbc.queryForObject("""
                        SELECT event_id, chat_id, message_text, attempts, expires_at
                        FROM telegram_notification_outbox WHERE event_id = ? AND state = 'SENDING'
                        """, (rs, row) -> new OutboxMessage(
                        rs.getString("event_id"), rs.getString("chat_id"), rs.getString("message_text"),
                        rs.getInt("attempts"), rs.getTimestamp("expires_at").toInstant()), eventId);
                return Optional.ofNullable(message);
            }
        }
        return Optional.empty();
    }

    @Override
    public void markSent(String eventId, int claimedAttempt) {
        jdbc.update("""
                UPDATE telegram_notification_outbox
                SET state = 'SENT', lease_until = NULL
                WHERE event_id = ? AND state = 'SENDING' AND attempts = ?
                """, eventId, claimedAttempt);
    }

    @Override
    public void markRetry(String eventId, int claimedAttempt, Instant nextAttemptAt, String failureKind) {
        jdbc.update("""
                UPDATE telegram_notification_outbox
                SET state = 'RETRY', next_attempt_at = ?, lease_until = NULL, last_failure_kind = ?
                WHERE event_id = ? AND state = 'SENDING' AND attempts = ?
                """, timestamp(nextAttemptAt), safeFailureKind(failureKind), eventId, claimedAttempt);
    }

    @Override
    public void markFailed(String eventId, int claimedAttempt, String failureKind) {
        jdbc.update("""
                UPDATE telegram_notification_outbox
                SET state = 'FAILED', lease_until = NULL, last_failure_kind = ?
                WHERE event_id = ? AND state = 'SENDING' AND attempts = ?
                """, safeFailureKind(failureKind), eventId, claimedAttempt);
    }

    @Override
    public void markExpired(String eventId, int claimedAttempt) {
        jdbc.update("""
                UPDATE telegram_notification_outbox
                SET state = 'EXPIRED', lease_until = NULL, last_failure_kind = 'expires_before_retry'
                WHERE event_id = ? AND state = 'SENDING' AND attempts = ?
                """, eventId, claimedAttempt);
    }

    @Override
    public int expireDue(Instant now) {
        return jdbc.update("""
                UPDATE telegram_notification_outbox
                SET state = 'EXPIRED', lease_until = NULL
                WHERE expires_at <= ? AND (
                    state IN ('PENDING', 'RETRY')
                    OR (state = 'SENDING' AND lease_until <= ?)
                )
                """, timestamp(now), timestamp(now));
    }

    @Override
    public int purgeTerminalBefore(Instant cutoff) {
        return jdbc.update("""
                DELETE FROM telegram_notification_outbox
                WHERE state IN ('SENT', 'FAILED', 'EXPIRED') AND created_at < ?
                """, timestamp(cutoff));
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private String safeFailureKind(String value) {
        if (value == null || !value.matches("[a-z_]{1,32}")) {
            return "unknown";
        }
        return value;
    }
}
