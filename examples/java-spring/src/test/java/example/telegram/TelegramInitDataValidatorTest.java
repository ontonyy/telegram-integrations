package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TelegramInitDataValidatorTest {
    private static final String BOT_TOKEN = "synthetic-test-token";
    private final TelegramInitDataValidator validator = new TelegramInitDataValidator();

    @Test
    void acceptsFreshDataWithAValidTelegramStyleSignature() {
        String user = "{\"id\":12345,\"first_name\":\"Test\"}";
        Map<String, String> values = Map.of(
                "auth_date", Long.toString(Instant.now().getEpochSecond()),
                "query_id", "synthetic-query",
                "user", user);

        Map<String, String> validated = validator.validate(signedInitData(values), BOT_TOKEN);

        assertEquals(user, validated.get("user"));
        assertEquals("synthetic-query", validated.get("query_id"));
    }

    @Test
    void rejectsTamperedData() {
        String initData = signedInitData(Map.of(
                "auth_date", Long.toString(Instant.now().getEpochSecond()),
                "query_id", "synthetic-query"));

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(initData.replace("synthetic-query", "changed-query"), BOT_TOKEN));
    }

    @Test
    void rejectsStaleSignedData() {
        String initData = signedInitData(Map.of(
                "auth_date", Long.toString(Instant.now().getEpochSecond() - 3_600),
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
        byte[] secret = hmac(BOT_TOKEN.getBytes(StandardCharsets.UTF_8), "WebAppData");
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
