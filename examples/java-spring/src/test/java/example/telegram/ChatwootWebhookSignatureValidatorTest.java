package example.telegram;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ChatwootWebhookSignatureValidatorTest {
    private static final String SECRET = "local-chatwoot-test-secret";
    private static final String TIMESTAMP = "1795521600";
    private static final byte[] BODY = ("{\"event\":\"message_created\",\"id\":\"1234\","
            + "\"content\":\"Synthetic support question\",\"message_type\":\"incoming\","
            + "\"content_type\":\"text\",\"sender\":{\"type\":\"contact\",\"id\":\"77\"},"
            + "\"conversation\":{\"id\":481},\"account\":{\"id\":12}}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final String INDEPENDENT_SIGNATURE =
            "sha256=0bddfef6960e339608ef9b5ee7d2e94dfd5b154ee401d7c10d57681aae1c5ad5";

    private final ChatwootWebhookSignatureValidator validator = new ChatwootWebhookSignatureValidator(
            Clock.fixed(Instant.ofEpochSecond(1_795_521_600L), ZoneOffset.UTC));

    @Test
    void acceptsAnIndependentlyGeneratedChatwootSignatureVector() {
        assertTrue(validator.isValid(BODY, TIMESTAMP, INDEPENDENT_SIGNATURE, SECRET));
    }

    @Test
    void rejectsChangedRawBodyAndTimestampsOutsideTheReplayWindow() {
        byte[] tamperedBody = BODY.clone();
        tamperedBody[0] = '[';

        assertFalse(validator.isValid(tamperedBody, TIMESTAMP, INDEPENDENT_SIGNATURE, SECRET));
        assertFalse(validator.isValid(BODY, "1795521299", INDEPENDENT_SIGNATURE, SECRET));
        assertFalse(validator.isValid(BODY, "1795521901", INDEPENDENT_SIGNATURE, SECRET));
    }

    @Test
    void rejectsMissingSecretMalformedSignatureAndTimestamp() {
        assertFalse(validator.isValid(BODY, TIMESTAMP, INDEPENDENT_SIGNATURE, " "));
        assertFalse(validator.isValid(BODY, TIMESTAMP, "not-a-signature", SECRET));
        assertFalse(validator.isValid(BODY, "not-a-timestamp", INDEPENDENT_SIGNATURE, SECRET));
    }
}
