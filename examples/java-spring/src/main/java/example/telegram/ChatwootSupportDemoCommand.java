package example.telegram;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;

/** Runs a fixed, offline Chatwoot webhook and operator handoff walkthrough. */
public final class ChatwootSupportDemoCommand {
    private static final String SECRET = "local-chatwoot-demo-secret";
    private static final String TIMESTAMP = "1795521600";
    private static final Instant NOW = Instant.ofEpochSecond(1_795_521_600L);
    private static final String BODY = "{\"event\":\"message_created\",\"id\":\"1234\","
            + "\"content\":\"Synthetic support question\",\"message_type\":\"incoming\","
            + "\"content_type\":\"text\",\"sender\":{\"type\":\"contact\",\"id\":\"77\"},"
            + "\"conversation\":{\"id\":481},\"account\":{\"id\":12}}";

    private ChatwootSupportDemoCommand() {}

    public static void main(String[] args) {
        RecordingChatwootActions actions = new RecordingChatwootActions();
        ChatwootSupportHandoff handoff = new ChatwootSupportHandoff(Set.of("operator-demo"), actions);
        ChatwootWebhookProcessor processor = new ChatwootWebhookProcessor(
                new ChatwootWebhookSignatureValidator(Clock.fixed(NOW, ZoneOffset.UTC)),
                new InMemoryChatwootWebhookEventRegistry(), handoff, new ObjectMapper());
        byte[] rawBody = BODY.getBytes(StandardCharsets.UTF_8);
        String signature = sign(rawBody, TIMESTAMP);

        System.out.println("Chatwoot support webhook lab (offline; synthetic data only)");
        System.out.println("valid incoming message: " + processor.process(rawBody, TIMESTAMP, signature, SECRET));
        System.out.println("same message delivered again: " + processor.process(rawBody, TIMESTAMP, signature, SECRET));
        System.out.println("wrong signature: " + processor.process(rawBody, TIMESTAMP, "sha256=" + "0".repeat(64), SECRET));

        String staleTimestamp = "1795521299";
        System.out.println("stale signed event: "
                + processor.process(rawBody, staleTimestamp, sign(rawBody, staleTimestamp), SECRET));
        System.out.println("unauthorized operator: " + handoff.assign(481, "operator-unknown").message());
        System.out.println("authorized operator assignment accepted: " + handoff.assign(481, "operator-demo").accepted());
        System.out.println("authorized reply request accepted: "
                + handoff.reply(481, "operator-demo", "Synthetic operator reply").accepted());
        actions.actions().forEach(action -> System.out.println("recorded fake Chatwoot action: " + action));
        System.out.println("Message text, signatures and webhook secrets are not printed.");
    }

    private static String sign(byte[] rawBody, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create the synthetic Chatwoot event", exception);
        }
    }
}
