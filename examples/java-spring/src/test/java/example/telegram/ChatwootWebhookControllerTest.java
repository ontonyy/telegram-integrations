package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class ChatwootWebhookControllerTest {
    private static final String SECRET = "local-chatwoot-test-secret";
    private static final String TIMESTAMP = "1795521600";
    private static final String BODY = "{\"event\":\"message_created\",\"id\":\"1234\","
            + "\"content\":\"Synthetic support question\",\"message_type\":\"incoming\","
            + "\"content_type\":\"text\",\"sender\":{\"type\":\"contact\",\"id\":\"77\"},"
            + "\"conversation\":{\"id\":481},\"account\":{\"id\":12}}";
    private static final String SIGNATURE =
            "sha256=0bddfef6960e339608ef9b5ee7d2e94dfd5b154ee401d7c10d57681aae1c5ad5";

    private final ObjectMapper mapper = new ObjectMapper();
    private final RecordingActions actions = new RecordingActions();
    private final ChatwootSupportHandoff handoff = new ChatwootSupportHandoff(Set.of("operator-7"), actions);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = controller(SECRET);
    }

    @Test
    void acceptsIncomingEventAndDeduplicatesByMessageIdRatherThanDeliveryHeader() throws Exception {
        sendSigned(BODY, TIMESTAMP, SIGNATURE, "delivery-a").andExpect(status().isOk());
        sendSigned(BODY, TIMESTAMP, SIGNATURE, "delivery-b").andExpect(status().isOk());

        assertTrue(handoff.assign(481, "operator-7").accepted());
        assertTrue(handoff.reply(481, "operator-7", "Synthetic reply").accepted());
        assertEquals(List.of("assign:481:operator-7", "reply:481"), actions.calls);
    }

    @Test
    void rejectsInvalidSignatureAndStaleTimestampBeforeProcessing() throws Exception {
        sendSigned(BODY, TIMESTAMP, "sha256=" + "0".repeat(64), "delivery-invalid")
                .andExpect(status().isUnauthorized());
        sendSigned(BODY, "1795521299", SIGNATURE, "delivery-stale")
                .andExpect(status().isUnauthorized());

        assertTrue(actions.calls.isEmpty());
    }

    @Test
    void rejectsWhenNoSecretIsConfiguredAndAcknowledgesNonIncomingEventsWithoutHandoff() throws Exception {
        controller("").perform(post("/chatwoot/webhook")
                .content(BODY.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isServiceUnavailable());

        String outgoing = BODY.replace("incoming", "outgoing");
        sendSigned(outgoing, TIMESTAMP, sign(outgoing.getBytes(StandardCharsets.UTF_8), TIMESTAMP), "delivery-outgoing")
                .andExpect(status().isOk());
        sendSigned(BODY, TIMESTAMP, SIGNATURE, null).andExpect(status().isOk());
        assertTrue(actions.calls.isEmpty());
    }

    @Test
    void rejectsMalformedSignedPayload() throws Exception {
        String malformed = "{";
        sendSigned(malformed, TIMESTAMP, sign(malformed.getBytes(StandardCharsets.UTF_8), TIMESTAMP), "delivery-bad-json")
                .andExpect(status().isBadRequest());
        assertTrue(actions.calls.isEmpty());
    }

    private MockMvc controller(String secret) {
        ChatwootWebhookProcessor processor = new ChatwootWebhookProcessor(
                new ChatwootWebhookSignatureValidator(
                        Clock.fixed(Instant.ofEpochSecond(1_795_521_600L), ZoneOffset.UTC)),
                new InMemoryChatwootWebhookEventRegistry(), handoff, mapper);
        ChatwootWebhookController controller = new ChatwootWebhookController(secret, 128_000, processor);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private org.springframework.test.web.servlet.ResultActions sendSigned(
            String body, String timestamp, String signature, String deliveryId) throws Exception {
        var request = post("/chatwoot/webhook")
                .header("X-Chatwoot-Signature", signature)
                .header("X-Chatwoot-Timestamp", timestamp);
        if (deliveryId != null) {
            request.header("X-Chatwoot-Delivery", deliveryId);
        }
        return mockMvc.perform(request.contentType("application/json")
                .content(body.getBytes(StandardCharsets.UTF_8)));
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
