package example.telegram;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class JiraWebhookSignatureValidator {
    public boolean isValid(byte[] payload, String suppliedSignature, String secret) {
        if (payload == null || suppliedSignature == null || secret == null || secret.isBlank()) {
            return false;
        }
        String[] parts = suppliedSignature.split("=", 2);
        if (parts.length != 2 || !"sha256".equals(parts[0]) || !parts[1].matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    suppliedSignature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate Jira webhook signature", exception);
        }
    }
}
