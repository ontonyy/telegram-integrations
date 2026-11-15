package example.telegram;

import java.time.Instant;

public record OutboxMessage(
        String eventId,
        String chatId,
        String text,
        int attempts,
        Instant expiresAt) {}
