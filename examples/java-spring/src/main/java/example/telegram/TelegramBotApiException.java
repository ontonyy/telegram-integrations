package example.telegram;

import java.time.Duration;

public class TelegramBotApiException extends IllegalStateException {
    private final int statusCode;
    private final Duration retryAfter;

    public TelegramBotApiException(int statusCode, Duration retryAfter) {
        super("Telegram Bot API returned HTTP " + statusCode);
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
    }

    public int statusCode() { return statusCode; }

    public Duration retryAfter() { return retryAfter; }
}
