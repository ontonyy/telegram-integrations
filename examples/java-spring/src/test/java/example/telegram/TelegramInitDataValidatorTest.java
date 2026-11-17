package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TelegramInitDataValidatorTest {
    private static final String BOT_TOKEN = "synthetic-test-token";
    private static final Instant NOW = Instant.ofEpochSecond(1_704_067_800L);
    private final TelegramInitDataValidator validator = new TelegramInitDataValidator(
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsIndependentFixedTelegramSignatureVector() {
        String initData = "auth_date=1704067200&query_id=q1&user=%7B%22id%22%3A123%2C%22first_name%22%3A%22Ada%22%7D"
                + "&hash=35c6a3516d3a9faf6a167b40fa5cfff07c404839bb8fff73fce3cfd69869879f";

        Map<String, String> validated = validator.validate(initData, "synthetic:test-token");

        assertEquals("q1", validated.get("query_id"));
        assertEquals("{\"id\":123,\"first_name\":\"Ada\"}", validated.get("user"));
    }

    @Test
    void acceptsFreshDataWithAValidTelegramStyleSignature() {
        String user = "{\"id\":12345,\"first_name\":\"Test\"}";
        Map<String, String> values = Map.of(
                "auth_date", Long.toString(NOW.getEpochSecond()),
                "query_id", "synthetic-query",
                "user", user);

        Map<String, String> validated = validator.validate(signedInitData(values), BOT_TOKEN);

        assertEquals(user, validated.get("user"));
        assertEquals("synthetic-query", validated.get("query_id"));
    }

    @Test
    void rejectsTamperedData() {
        String initData = signedInitData(Map.of(
                "auth_date", Long.toString(NOW.getEpochSecond()),
                "query_id", "synthetic-query"));

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(initData.replace("synthetic-query", "changed-query"), BOT_TOKEN));
    }

    @Test
    void rejectsStaleSignedData() {
        String initData = signedInitData(Map.of(
                "auth_date", Long.toString(NOW.getEpochSecond() - 901),
                "query_id", "synthetic-query"));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(initData, BOT_TOKEN));
    }

    @Test
    void rejectsDuplicateKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("auth_date=1&auth_date=2&hash=invalid", BOT_TOKEN));
    }

    private String signedInitData(Map<String, String> values) {
        Map<String, String> sorted = new TreeMap<>(values);
        String dataCheckString = sorted.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), BOT_TOKEN);
        String hash = HexFormatSupport.toHex(hmac(secret, dataCheckString));
        return sorted.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElseThrow()
                + "&hash=" + hash;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
