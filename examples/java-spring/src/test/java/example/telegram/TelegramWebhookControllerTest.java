package example.telegram;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class TelegramWebhookControllerTest {
    private static final String SECRET = "synthetic-webhook-secret";
    private final ObjectMapper mapper = new ObjectMapper();
    private RecordingTelegramBotClient telegram;
    private BusinessSupportService businessSupport;
    private AtomicInteger salesUpserts;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        telegram = new RecordingTelegramBotClient();
        salesUpserts = new AtomicInteger();
        businessSupport = new BusinessSupportService(telegram, "operator-1"::equals);
        BotReplyService replies = new BotReplyService(
                telegram, new ClaudeClient("", "", mapper, request -> new TransportResponse(200, "{}")));
        TelegramWebhookController controller = new TelegramWebhookController(
                SECRET, replies, new InMemoryTelegramUpdateRegistry(),
                new GroupVoteService((chatId, userId) -> "10".equals(userId)),
                new GrafanaAlertService(replies, "", ""), noFileIntake(),
                businessSupport,
                new SalesLeadService(contact -> {
                    salesUpserts.incrementAndGet();
                    return "9001";
                }, telegram, "-900", "Demo sales queue"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void acceptsValidUpdateAndDoesNotRepeatItsSideEffect() throws Exception {
        String update = update(42, "/start");

        send(update, SECRET).andExpect(status().isOk());
        send(update, SECRET).andExpect(status().isOk());

        assertEquals(1, telegram.sendCalls);
    }

    @Test
    void salesFlowIsRoutedThroughWebhookAndDuplicateUpdateIsNotRepeated() throws Exception {
        send(update(48, "/sales"), SECRET).andExpect(status().isOk());
        send(update(49, "/sales-consent"), SECRET).andExpect(status().isOk());
        String submission = update(50, "/sales-submit lead@example.test|Ada|Lovelace|-");
        send(submission, SECRET).andExpect(status().isOk());
        int sendsAfterSubmit = telegram.sendCalls;
        send(submission, SECRET).andExpect(status().isOk());

        assertEquals(1, salesUpserts.get());
        assertEquals(sendsAfterSubmit, telegram.sendCalls);
    }

    @Test
    void rejectsMissingAndIncorrectWebhookSecrets() throws Exception {
        send(update(43, "/start"), "wrong-secret").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update(44, "/start")))
                .andExpect(status().isUnauthorized());

        assertEquals(0, telegram.sendCalls);
    }

    @Test
    void rejectsAnUpdateWithoutUpdateIdAndIgnoresUnsupportedUpdateShapes() throws Exception {
        send("{\"message\":{\"text\":\"/start\"}}", SECRET).andExpect(status().isBadRequest());
        send("{\"update_id\":\"45\",\"message\":{}}", SECRET).andExpect(status().isBadRequest());
        send("{\"update_id\":45,\"callback_query\":{}}", SECRET).andExpect(status().isOk());

        assertEquals(0, telegram.sendCalls);
    }

    @Test
    void refusesWebhookWhenNoSecretIsConfigured() throws Exception {
        BotReplyService replies = new BotReplyService(
                telegram, new ClaudeClient("", "", mapper, request -> new TransportResponse(200, "{}")));
        TelegramWebhookController controller = new TelegramWebhookController(
                "", replies, new InMemoryTelegramUpdateRegistry(),
                new GroupVoteService((chatId, userId) -> false),
                new GrafanaAlertService(replies, "", ""), noFileIntake(),
                new BusinessSupportService(telegram, "operator-1"::equals),
                new SalesLeadService(contact -> "9001", telegram, "", "Demo sales queue"));
        MockMvc unconfigured = MockMvcBuilders.standaloneSetup(controller).build();

        unconfigured.perform(post("/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update(46, "/start")))
                .andExpect(status().isServiceUnavailable());
        assertEquals(0, telegram.sendCalls);
    }

    @Test
    void failedActionCanBeRetriedWithoutBeingMarkedComplete() throws Exception {
        String update = update(47, "/start");
        telegram.failNextSend = true;

        send(update, SECRET).andExpect(status().isServiceUnavailable());
        send(update, SECRET).andExpect(status().isOk());

        assertEquals(2, telegram.sendCalls);
    }

    @Test
    void groupVoteRecordsAllowedUserAndMakesRepeatedUpdatesIdempotent() throws Exception {
        send(groupUpdate(100, 10, "/vote"), SECRET).andExpect(status().isOk());
        assertEquals(1, telegram.votePromptCalls);
        String voteId = telegram.votePrompt.text().split("Vote ID: ")[1].split("\\n")[0];
        String callback = "vote|" + voteId + "|0";
        String firstTap = callbackUpdate(101, 77, 10, callback);

        send(firstTap, SECRET).andExpect(status().isOk());
        assertEquals(1, telegram.callbackAnswers);
        assertEquals(1, telegram.voteEdits);
        assertTrue(telegram.lastVoteMessage.contains("Tuesday 10:00: 1"));

        send(firstTap, SECRET).andExpect(status().isOk());
        assertEquals(2, telegram.callbackAnswers);
        assertEquals(1, telegram.voteEdits);

        send(callbackUpdate(102, 78, 10, "vote|" + voteId + "|1"), SECRET)
                .andExpect(status().isOk());
        assertEquals(2, telegram.voteEdits);
        assertTrue(telegram.lastVoteMessage.contains("Tuesday 10:00: 0"));
        assertTrue(telegram.lastVoteMessage.contains("Wednesday 14:00: 1"));
    }

    @Test
    void groupVoteRejectsUnlistedUsersAndOnlyCreatorCanClose() throws Exception {
        send(groupUpdate(200, 10, "/vote"), SECRET).andExpect(status().isOk());
        String voteId = telegram.votePrompt.text().split("Vote ID: ")[1].split("\\n")[0];

        send(callbackUpdate(201, 88, 11, "vote|" + voteId + "|0"), SECRET)
                .andExpect(status().isOk());
        assertEquals(1, telegram.callbackAnswers);
        assertTrue(telegram.lastCallbackAnswer.contains("not allowed"));
        assertEquals(0, telegram.voteEdits);

        send(groupUpdate(202, 11, "/closevote " + voteId), SECRET).andExpect(status().isOk());
        assertEquals(0, telegram.sendCalls);
        send(groupUpdate(203, 10, "/closevote " + voteId), SECRET).andExpect(status().isOk());
        assertEquals(1, telegram.sendCalls);
        assertTrue(telegram.lastSentMessage.startsWith("FINAL RESULTS"));
    }

    @Test
    void ignoresVoteCommandsOutsideGroups() throws Exception {
        send(update(300, "/vote"), SECRET).andExpect(status().isOk());
        assertEquals(0, telegram.votePromptCalls);
    }

    @Test
    void routesAndDeduplicatesBusinessConnectionAndCustomerUpdates() throws Exception {
        send(mapper.writeValueAsString(Map.of(
                "update_id", 401,
                "business_connection", Map.of(
                        "id", "business-connection-demo",
                        "user", Map.of("id", 900),
                        "is_enabled", true,
                        "rights", Map.of("can_reply", true)))), SECRET)
                .andExpect(status().isOk());
        String inbound = mapper.writeValueAsString(Map.of(
                "update_id", 402,
                "business_message", Map.of(
                        "business_connection_id", "business-connection-demo",
                        "message_id", 700,
                        "date", java.time.Instant.now().getEpochSecond(),
                        "chat", Map.of("id", 555, "type", "private"),
                        "from", Map.of("id", 42),
                        "text", "synthetic business customer text")));

        send(inbound, SECRET).andExpect(status().isOk());
        send(inbound, SECRET).andExpect(status().isOk());

        assertEquals(1, businessSupport.tickets().size());
        assertEquals("OPEN", businessSupport.tickets().getFirst().status());
        assertEquals(0, telegram.businessSendCalls);
        assertTrue(!businessSupport.tickets().getFirst().toString()
                .contains("synthetic business customer text"));
    }

    private org.springframework.test.web.servlet.ResultActions send(String update, String secret) throws Exception {
        return mockMvc.perform(post("/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", secret)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update));
    }

    private String update(long updateId, String text) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "update_id", updateId,
                "message", Map.of(
                        "message_id", updateId,
                        "chat", Map.of("id", 12345, "type", "private"),
                        "from", Map.of("id", 12345),
                        "text", text)));
    }

    private String groupUpdate(long updateId, long userId, String text) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "update_id", updateId,
                "message", Map.of(
                        "message_id", updateId,
                        "chat", Map.of("id", -12345, "type", "supergroup"),
                        "from", Map.of("id", userId),
                        "text", text)));
    }

    private String callbackUpdate(long updateId, long callbackId, long userId, String data) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "update_id", updateId,
                "callback_query", Map.of(
                        "id", "callback-" + callbackId,
                        "from", Map.of("id", userId),
                        "message", Map.of(
                                "message_id", 501,
                                "chat", Map.of("id", -12345, "type", "supergroup")),
                        "data", data)));
    }

    private FileIntakeService noFileIntake() {
        return new FileIntakeService(new TelegramFileGateway() {
            @Override
            public DownloadInfo getFile(String fileId) {
                throw new AssertionError("Unexpected test file download");
            }

            @Override
            public void download(String filePath, java.nio.file.Path destination, long maxBytes) {
                throw new AssertionError("Unexpected test file download");
            }
        }, 1_024, java.nio.file.Path.of("build", "unused-file-intake-test"));
    }

    private static final class RecordingTelegramBotClient extends TelegramBotClient {
        private int sendCalls;
        private int votePromptCalls;
        private int voteEdits;
        private int callbackAnswers;
        private int businessSendCalls;
        private boolean failNextSend;
        private String lastSentMessage;
        private String lastBusinessConnectionId;
        private String lastBusinessChatId;
        private String lastBusinessMessage;
        private String lastVoteMessage;
        private String lastCallbackAnswer;
        private GroupVoteService.VotePrompt votePrompt;

        private RecordingTelegramBotClient() {
            super("", false, "https://api.telegram.org", Duration.ofSeconds(1), new ObjectMapper(),
                    request -> new TransportResponse(200, "{}"));
        }

        @Override
        public void sendOrLog(String chatId, String text) {
            sendCalls++;
            lastSentMessage = text;
            if (failNextSend) {
                failNextSend = false;
                throw new IllegalStateException("synthetic delivery failure");
            }
        }

        @Override
        public void sendBusinessMessageOrLog(String connectionId, String chatId, String text) {
            businessSendCalls++;
            lastBusinessConnectionId = connectionId;
            lastBusinessChatId = chatId;
            lastBusinessMessage = text;
            if (failNextSend) {
                failNextSend = false;
                throw new IllegalStateException("synthetic Business delivery failure");
            }
        }

        @Override
        public void sendMessageWithInlineKeyboardOrLog(
                String chatId, String text, List<List<InlineButton>> keyboard) {
            votePromptCalls++;
            votePrompt = new GroupVoteService.VotePrompt("", text, keyboard);
        }

        @Override
        public void editMessageWithInlineKeyboardOrLog(
                String chatId, long messageId, String text, List<List<InlineButton>> keyboard) {
            voteEdits++;
            lastVoteMessage = text;
        }

        @Override
        public void answerCallbackQueryOrLog(String callbackId, String text) {
            callbackAnswers++;
            lastCallbackAnswer = text;
        }
    }
}
