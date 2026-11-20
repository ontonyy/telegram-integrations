package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class FileIntakeWebhookTest {
    private static final String SECRET = "synthetic-telegram-webhook-secret";
    @TempDir
    Path tempDir;
    private final ObjectMapper mapper = new ObjectMapper();
    private FakeGateway gateway;
    private RecordingTelegramClient telegram;
    private BotReplyService replies;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        gateway = new FakeGateway();
        telegram = new RecordingTelegramClient();
        replies = new BotReplyService(
                telegram, new ClaudeClient("", "", mapper, request -> new TransportResponse(200, "{}")));
        FileIntakeService files = new FileIntakeService(gateway, 1_024, tempDir);
        TelegramWebhookController controller = new TelegramWebhookController(
                SECRET,
                replies,
                new InMemoryTelegramUpdateRegistry(),
                new GroupVoteService((chatId, userId) -> false),
                new GrafanaAlertService(replies, "", ""),
                files,
                new BusinessSupportService(telegram, operatorId -> false),
                new SalesLeadService(contact -> "9001", telegram, "", "Demo sales queue"),
                new InternalRequestService(userId -> java.util.Set.of(), telegram, "-901"),
                new TelegramStarsPaymentService(new SimulatedTelegramStarsGateway()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void requiresPrivateDisclosureAndConsentBeforeTemporaryProcessing() throws Exception {
        send(textUpdate(1, "/file-consent"), SECRET).andExpect(status().isOk());
        assertTrue(telegram.lastMessage.contains("Send /file-help first"));
        assertFalse(replies.hasFileConsent("123"));

        send(textUpdate(2, "/file-help"), SECRET).andExpect(status().isOk());
        send(textUpdate(3, "/file-consent"), SECRET).andExpect(status().isOk());
        assertTrue(replies.hasFileConsent("123"));

        send(documentUpdate(4, "private"), SECRET).andExpect(status().isOk());

        assertEquals(1, gateway.downloadCalls);
        assertTrue(telegram.lastMessage.contains("2 lines"));
        assertTrue(telegram.lastMessage.contains("deleted"));
        assertFalse(telegram.lastMessage.contains("Sensitive synthetic stack trace"));
        try (var remaining = Files.list(tempDir)) {
            assertEquals(java.util.List.of(".maintenance.lock"),
                    remaining.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void doesNotDownloadFilesSentIntoGroups() throws Exception {
        replies.handle("/file-help", "private", "123", "123");
        replies.handle("/file-consent", "private", "123", "123");

        send(documentUpdate(5, "supergroup"), SECRET).andExpect(status().isOk());

        assertEquals(0, gateway.downloadCalls);
    }

    private org.springframework.test.web.servlet.ResultActions send(String update, String secret) throws Exception {
        return mockMvc.perform(post("/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", secret)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update));
    }

    private String textUpdate(long id, String text) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "update_id", id,
                "message", Map.of(
                        "chat", Map.of("id", 123, "type", "private"),
                        "from", Map.of("id", 123),
                        "text", text)));
    }

    private String documentUpdate(long id, String chatType) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "update_id", id,
                "message", Map.of(
                        "chat", Map.of("id", 123, "type", chatType),
                        "from", Map.of("id", 123),
                        "document", Map.of(
                                "file_id", "synthetic-file-id",
                                "file_name", "trace.log",
                                "mime_type", "text/plain",
                                "file_size", gateway.content.length))));
    }

    private static final class FakeGateway implements TelegramFileGateway {
        private final byte[] content = "Sensitive synthetic stack trace\nline two\n"
                .getBytes(StandardCharsets.UTF_8);
        private int downloadCalls;

        @Override
        public DownloadInfo getFile(String fileId) {
            return new DownloadInfo("documents/synthetic.log", content.length);
        }

        @Override
        public void download(String filePath, Path destination, long maxBytes) throws java.io.IOException {
            downloadCalls++;
            Files.write(destination, content);
        }
    }

    private static final class RecordingTelegramClient extends TelegramBotClient {
        private String lastMessage;

        private RecordingTelegramClient() {
            super("", false, "https://api.telegram.org", Duration.ofSeconds(1), new ObjectMapper(),
                    request -> new TransportResponse(200, "{}"));
        }

        @Override
        public void sendOrLog(String chatId, String text) {
            lastMessage = text;
        }
    }
}
