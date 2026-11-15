package example.telegram;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;
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
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        telegram = new RecordingTelegramBotClient();
        BotReplyService replies = new BotReplyService(
                telegram, new ClaudeClient("", "", mapper, request -> new TransportResponse(200, "{}")));
        TelegramWebhookController controller = new TelegramWebhookController(
                SECRET, replies, new InMemoryTelegramUpdateRegistry());
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
                "", replies, new InMemoryTelegramUpdateRegistry());
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

    private static final class RecordingTelegramBotClient extends TelegramBotClient {
        private int sendCalls;
        private boolean failNextSend;

        private RecordingTelegramBotClient() {
            super("", false, "https://api.telegram.org", Duration.ofSeconds(1), new ObjectMapper(),
                    request -> new TransportResponse(200, "{}"));
        }

        @Override
        public void sendOrLog(String chatId, String text) {
            sendCalls++;
            if (failNextSend) {
                failNextSend = false;
                throw new IllegalStateException("synthetic delivery failure");
            }
        }
    }
}
