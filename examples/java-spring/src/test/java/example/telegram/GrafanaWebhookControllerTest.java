package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class GrafanaWebhookControllerTest {
    private static final String GRAFANA_SECRET = "synthetic-grafana-webhook-secret";
    private static final String TELEGRAM_SECRET = "synthetic-telegram-webhook-secret";
    private final ObjectMapper mapper = new ObjectMapper();
    private RecordingTelegramClient telegram;
    private GrafanaWebhookSignatureValidator signatures;
    private MockMvc grafanaMvc;
    private MockMvc telegramMvc;

    @BeforeEach
    void setUp() {
        telegram = new RecordingTelegramClient();
        signatures = new GrafanaWebhookSignatureValidator();
        BotReplyService replies = new BotReplyService(
                telegram, new ClaudeClient("", "", mapper, request -> new TransportResponse(200, "{}")));
        GrafanaAlertService alerts = new GrafanaAlertService(replies, "-777", "7");
        GrafanaWebhookController grafanaController = new GrafanaWebhookController(
                GRAFANA_SECRET, "-777", signatures, alerts, mapper);
        TelegramWebhookController telegramController = new TelegramWebhookController(
                TELEGRAM_SECRET, replies, new InMemoryTelegramUpdateRegistry(),
                new GroupVoteService((chatId, userId) -> false), alerts,
                new FileIntakeService(new TelegramFileGateway() {
                    @Override
                    public DownloadInfo getFile(String fileId) {
                        throw new AssertionError("Unexpected test file download");
                    }

                    @Override
                    public void download(String filePath, java.nio.file.Path destination, long maxBytes) {
                        throw new AssertionError("Unexpected test file download");
                    }
                }, 1_024, java.nio.file.Path.of("build", "unused-file-intake-test")),
                new BusinessSupportService(telegram, operatorId -> false),
                new SalesLeadService(contact -> "9001", telegram, "", "Demo sales queue"),
                new InternalRequestService(userId -> java.util.Set.of(), telegram, "-901"));
        grafanaMvc = MockMvcBuilders.standaloneSetup(grafanaController)
                .addFilters(new TelegramWebhookBodyLimitFilter(1_048_576))
                .build();
        telegramMvc = MockMvcBuilders.standaloneSetup(telegramController).build();
    }

    @Test
    void validatesFiringAndResolvedPayloadAndIncludesOnlySafeFields() throws Exception {
        String firing = payload("firing", "High CPU <load>", "critical", "fp-1", "https://grafana.example/alert/1");
        sendGrafana(firing, Instant.now().getEpochSecond()).andExpect(status().isOk());
        assertEquals(1, telegram.sends);
        assertTrue(telegram.lastMessage.contains("FIRING: High CPU <load>"));
        assertTrue(telegram.lastMessage.contains("Severity: critical"));
        assertTrue(telegram.lastMessage.contains("Labels: team=platform"));
        assertTrue(telegram.lastMessage.contains("State: firing"));
        assertTrue(telegram.lastMessage.contains("https://grafana.example/alert/1"));
        assertFalse(telegram.lastMessage.contains("secret-internal-label"));
        assertTrue(telegram.lastKeyboard.getFirst().getFirst().callbackData().startsWith("grafana_ack|"));

        sendGrafana(payload("resolved", "High CPU <load>", "critical", "fp-1", ""),
                Instant.now().getEpochSecond()).andExpect(status().isOk());
        assertEquals(2, telegram.sends);
        assertTrue(telegram.lastMessage.startsWith("✅ RESOLVED"));
        assertTrue(telegram.lastKeyboard.isEmpty());
    }

    @Test
    void rejectsInvalidAndStaleWebhookSignatures() throws Exception {
        String payload = payload("firing", "Test alert", "warning", "fp-2", "");
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        grafanaMvc.perform(post("/grafana/webhook")
                        .header("X-Grafana-Alerting-Signature", "0".repeat(64))
                        .header("X-Grafana-Timestamp", Long.toString(Instant.now().getEpochSecond()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        sendGrafana(payload, Instant.now().getEpochSecond() - 900).andExpect(status().isUnauthorized());
        assertEquals(0, telegram.sends);
    }

    @Test
    void onlyAllowlistedUserCanAcknowledgeAndRepeatedAcknowledgementIsIdempotent() throws Exception {
        sendGrafana(payload("firing", "Database lag", "high", "fp-3", ""),
                Instant.now().getEpochSecond()).andExpect(status().isOk());
        String callbackData = telegram.lastKeyboard.getFirst().getFirst().callbackData();

        sendCallback(10, 81, callbackData).andExpect(status().isOk());
        assertTrue(telegram.lastCallbackAnswer.contains("not allowed"));
        assertEquals(0, telegram.edits);

        sendCallback(11, 7, callbackData).andExpect(status().isOk());
        assertTrue(telegram.lastCallbackAnswer.contains("application"));
        assertEquals(1, telegram.edits);
        assertTrue(telegram.lastEditedText.contains("application acknowledgement only"));
        assertFalse(telegram.lastEditedText.contains("resolved"));

        sendCallback(12, 7, callbackData).andExpect(status().isOk());
        assertTrue(telegram.lastCallbackAnswer.contains("Already acknowledged"));
        assertEquals(1, telegram.edits);
    }

    @Test
    void callbackUpdateDeduplicationDoesNotRepeatAcknowledgementMutation() throws Exception {
        sendGrafana(payload("firing", "Queue backlog", "warning", "fp-4", ""),
                Instant.now().getEpochSecond()).andExpect(status().isOk());
        String callbackData = telegram.lastKeyboard.getFirst().getFirst().callbackData();
        String callback = callbackUpdate(20, 7, callbackData);

        sendTelegramUpdate(callback).andExpect(status().isOk());
        sendTelegramUpdate(callback).andExpect(status().isOk());

        assertEquals(1, telegram.edits);
        assertTrue(telegram.lastCallbackAnswer.contains("already processed"));
    }

    @Test
    void retriesAlertDeliveryFailureWithoutMarkingTheRequestProcessed() throws Exception {
        telegram.failNext = true;
        String payload = payload("firing", "Disk full", "critical", "fp-5", "");
        sendGrafana(payload, Instant.now().getEpochSecond()).andExpect(status().isServiceUnavailable());
        sendGrafana(payload, Instant.now().getEpochSecond()).andExpect(status().isOk());
        assertEquals(2, telegram.sends);
    }

    private org.springframework.test.web.servlet.ResultActions sendGrafana(String payload, long timestamp)
            throws Exception {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        String time = Long.toString(timestamp);
        return grafanaMvc.perform(post("/grafana/webhook")
                .header("X-Grafana-Alerting-Signature", signature(body, time))
                .header("X-Grafana-Timestamp", time)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions sendCallback(long updateId, long userId, String data)
            throws Exception {
        return sendTelegramUpdate(callbackUpdate(updateId, userId, data));
    }

    private org.springframework.test.web.servlet.ResultActions sendTelegramUpdate(String update) throws Exception {
        return telegramMvc.perform(post("/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", TELEGRAM_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update));
    }

    private String callbackUpdate(long updateId, long userId, String data) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "update_id", updateId,
                "callback_query", Map.of(
                        "id", "callback-" + updateId,
                        "from", Map.of("id", userId),
                        "message", Map.of(
                                "message_id", 501,
                                "chat", Map.of("id", -777, "type", "supergroup")),
                        "data", data)));
    }

    private String payload(String status, String name, String severity, String fingerprint, String source)
            throws Exception {
        return mapper.writeValueAsString(Map.of(
                "status", status,
                "state", "firing".equals(status) ? "alerting" : "ok",
                "orgId", 1,
                "alerts", List.of(Map.of(
                        "status", status,
                        "labels", Map.of("alertname", name, "severity", severity,
                                "team", "platform", "secret-internal-label", "do not forward"),
                        "annotations", Map.of("description", "do not forward this description"),
                        "startsAt", "2026-11-15T10:00:00Z",
                        "endsAt", "resolved".equals(status) ? "2026-11-15T10:05:00Z" : "0001-01-01T00:00:00Z",
                        "generatorURL", source,
                        "fingerprint", fingerprint))));
    }

    private String signature(byte[] body, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(GRAFANA_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) ':');
            return java.util.HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class RecordingTelegramClient extends TelegramBotClient {
        private int sends;
        private int edits;
        private boolean failNext;
        private String lastMessage;
        private String lastEditedText;
        private String lastCallbackAnswer;
        private List<List<InlineButton>> lastKeyboard = List.of();

        private RecordingTelegramClient() {
            super("", false, "https://api.telegram.org", Duration.ofSeconds(1), new ObjectMapper(),
                    request -> new TransportResponse(200, "{}"));
        }

        @Override
        public void sendMessageWithInlineKeyboardOrLog(
                String chatId, String text, List<List<InlineButton>> keyboard) {
            sends++;
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("synthetic Telegram delivery failure");
            }
            lastMessage = text;
            lastKeyboard = keyboard;
        }

        @Override
        public void editMessageWithInlineKeyboardOrLog(
                String chatId, long messageId, String text, List<List<InlineButton>> keyboard) {
            edits++;
            lastEditedText = text;
        }

        @Override
        public void answerCallbackQueryOrLog(String callbackId, String text) {
            lastCallbackAnswer = text;
        }
    }
}
