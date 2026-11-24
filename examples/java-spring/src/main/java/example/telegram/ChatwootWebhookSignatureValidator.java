package example.telegram;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Verifies Chatwoot's signed outgoing webhook headers over the timestamp and exact raw body. */
@Component
public final class ChatwootWebhookSignatureValidator {
    private static final Duration MAX_TIMESTAMP_SKEW = Duration.ofMinutes(5);
    private final Clock clock;

    public ChatwootWebhookSignatureValidator() {
        this(Clock.systemUTC());
    }

    ChatwootWebhookSignatureValidator(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean isValid(byte[] rawBody, String timestamp, String signature, String secret) {
        if (rawBody == null || rawBody.length == 0 || timestamp == null || signature == null
                || secret == null || secret.isBlank() || !timestamp.matches("[0-9]{1,12}")
                || !signature.matches("sha256=[0-9a-fA-F]{64}")) {
            return false;
        }

        Instant signedAt;
        try {
            signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
        } catch (NumberFormatException | DateTimeException exception) {
            return false;
        }
        Duration age = Duration.between(signedAt, clock.instant());
        if (age.compareTo(MAX_TIMESTAMP_SKEW) > 0 || age.compareTo(MAX_TIMESTAMP_SKEW.negated()) < 0) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate Chatwoot webhook signature", exception);
        }
    }
}
