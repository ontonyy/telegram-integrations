package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ChatwootWebhookProcessorTest {
    private static final String SECRET = "local-chatwoot-test-secret";
    private static final String TIMESTAMP = "1795521600";
    private static final String BODY = "{\"event\":\"message_created\",\"id\":\"1234\","
            + "\"content\":\"Synthetic support question\",\"message_type\":\"incoming\","
            + "\"content_type\":\"text\",\"sender\":{\"type\":\"contact\",\"id\":\"77\"},"
            + "\"conversation\":{\"id\":481},\"account\":{\"id\":12}}";
    private ChatwootWebhookProcessor processor;
    private ChatwootSupportHandoff handoff;
    private RecordingActions actions;

    @BeforeEach
    void setUp() {
        actions = new RecordingActions();
        handoff = new ChatwootSupportHandoff(Set.of("operator-7"), actions);
        processor = new ChatwootWebhookProcessor(
                new ChatwootWebhookSignatureValidator(
                        Clock.fixed(Instant.ofEpochSecond(1_795_521_600L), ZoneOffset.UTC)),
                new InMemoryChatwootWebhookEventRegistry(), handoff, new ObjectMapper());
    }

    @Test
    void acceptsIncomingMessageOnceAndKeepsTheMessageBodyOutOfTheHandoff() throws Exception {
        byte[] rawBody = BODY.getBytes(StandardCharsets.UTF_8);
        String signature = sign(rawBody, TIMESTAMP);

        assertEquals(ChatwootWebhookProcessor.Outcome.ACCEPTED,
                processor.process(rawBody, TIMESTAMP, signature, SECRET));
        assertEquals(ChatwootWebhookProcessor.Outcome.DUPLICATE,
                processor.process(rawBody, TIMESTAMP, signature, SECRET));
        assertTrue(actions.calls.isEmpty());
        assertTrue(handoff.assign(481, "operator-7").accepted());
        assertTrue(handoff.reply(481, "operator-7", "Synthetic reply").accepted());
        assertEquals(List.of("assign:481:operator-7", "reply:481"), actions.calls);
    }

    @Test
    void ignoresOutgoingMessagesAndRejectsBadSignaturesAndStaleTimestamps() throws Exception {
        String outgoing = BODY.replace("incoming", "outgoing");
        byte[] outgoingBody = outgoing.getBytes(StandardCharsets.UTF_8);
        assertEquals(ChatwootWebhookProcessor.Outcome.IGNORED,
                processor.process(outgoingBody, TIMESTAMP, sign(outgoingBody, TIMESTAMP), SECRET));
        assertEquals(ChatwootWebhookProcessor.Outcome.INVALID_SIGNATURE,
                processor.process(BODY.getBytes(StandardCharsets.UTF_8), TIMESTAMP, "sha256=" + "0".repeat(64), SECRET));
        assertEquals(ChatwootWebhookProcessor.Outcome.INVALID_SIGNATURE,
                processor.process(BODY.getBytes(StandardCharsets.UTF_8), "1795521299", "sha256=" + "0".repeat(64), SECRET));
        assertTrue(actions.calls.isEmpty());
    }

    @Test
    void rejectsMalformedSignedEvents() throws Exception {
        byte[] body = "{".getBytes(StandardCharsets.UTF_8);
        assertEquals(ChatwootWebhookProcessor.Outcome.INVALID_PAYLOAD,
                processor.process(body, TIMESTAMP, sign(body, TIMESTAMP), SECRET));
    }

    private String sign(byte[] rawBody, String timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(rawBody));
    }

    private static final class RecordingActions implements ChatwootSupportHandoff.ChatwootActions {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void assignConversation(long conversationId, String agentId) {
            calls.add("assign:" + conversationId + ":" + agentId);
        }

        @Override
        public void sendOperatorReply(long conversationId, String text) {
            calls.add("reply:" + conversationId);
        }
    }
}
