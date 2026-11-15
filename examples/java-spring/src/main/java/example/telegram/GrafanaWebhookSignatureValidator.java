package example.telegram;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class GrafanaWebhookSignatureValidator {
    private static final long MAX_AGE_SECONDS = 300;

    public boolean isValid(byte[] body, String signature, String timestamp, String secret) {
        if (body == null || signature == null || timestamp == null || secret == null || secret.isBlank()
                || !signature.matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        if (timestampSeconds < now - MAX_AGE_SECONDS || timestampSeconds > now + MAX_AGE_SECONDS) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) ':');
            String expected = HexFormat.of().formatHex(mac.doFinal(body));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate Grafana webhook signature", exception);
        }
    }
}
