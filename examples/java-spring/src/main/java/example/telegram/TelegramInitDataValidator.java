package example.telegram;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TelegramInitDataValidator {
    private final Clock clock;

    @Autowired
    public TelegramInitDataValidator() {
        this(Clock.systemUTC());
    }

    TelegramInitDataValidator(Clock clock) {
        this.clock = clock;
    }

    public Map<String, String> validate(String rawInitData, String botToken) {
        if (rawInitData == null || rawInitData.length() > 16_000 || botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("Missing or oversized initData, or bot token is not configured");
        }
        Map<String, String> values = parse(rawInitData);
        String suppliedHash = values.remove("hash");
        String authDate = values.get("auth_date");
        if (suppliedHash == null || authDate == null || !authDateIsFresh(authDate)) {
            throw new IllegalArgumentException("Missing signature or initData is stale");
        }
        String dataCheckString = new TreeMap<>(values).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken);
        String expectedHash = HexFormatSupport.toHex(hmac(secret, dataCheckString));
        if (!java.security.MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                suppliedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Invalid initData signature");
        }
        return Map.copyOf(values);
    }

    private Map<String, String> parse(String raw) {
        Map<String, String> values = new TreeMap<>();
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Malformed initData");
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            if (values.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate initData key");
        }
        return values;
    }

    private boolean authDateIsFresh(String value) {
        try {
            long timestamp = Long.parseLong(value);
            long age = clock.instant().getEpochSecond() - timestamp;
            return age >= -60 && age <= 900;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC operation failed", exception);
        }
    }
}
