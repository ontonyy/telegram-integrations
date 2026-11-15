package example.telegram;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JiraWebhookSignatureValidatorTest {
    private final JiraWebhookSignatureValidator validator = new JiraWebhookSignatureValidator();

    @Test
    void matchesAtlassianPublishedSha256Example() {
        assertTrue(validator.isValid(
                "Hello World!".getBytes(StandardCharsets.UTF_8),
                "sha256=a4771c39fbe90f317c7824e83ddef3caae9cb3d976c214ace1f2937e133263c9",
                "It's a Secret to Everybody"));
    }

    @Test
    void rejectsWrongSecretUnsupportedAlgorithmAndMalformedSignature() {
        byte[] payload = "Hello World!".getBytes(StandardCharsets.UTF_8);
        assertFalse(validator.isValid(payload,
                "sha256=a4771c39fbe90f317c7824e83ddef3caae9cb3d976c214ace1f2937e133263c9", "wrong"));
        assertFalse(validator.isValid(payload, "sha1=invalid", "It's a Secret to Everybody"));
        assertFalse(validator.isValid(payload, "not-a-signature", "It's a Secret to Everybody"));
    }
}
