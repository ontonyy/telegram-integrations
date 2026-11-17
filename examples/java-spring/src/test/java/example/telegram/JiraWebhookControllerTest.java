package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

class JiraWebhookControllerTest {
    private static final String SECRET = "synthetic-jira-webhook-secret";
    private final ObjectMapper mapper = new ObjectMapper();
    private RecordingTelegramClient telegram;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        telegram = new RecordingTelegramClient();
        JiraIssueNotificationService notifications = new JiraIssueNotificationService(
                telegram, "-123456", "DEMO", "Bug,Task", "In Progress,Done", "https://jira.example.test");
        JiraWebhookController controller = new JiraWebhookController(
                SECRET, new JiraWebhookSignatureValidator(), new JiraWebhookRegistry(), notifications, mapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new TelegramWebhookBodyLimitFilter(1_048_576))
                .build();
    }

    @Test
    void signsFiltersAndDeduplicatesStatusNotificationWithoutCopyingDescription() throws Exception {
        byte[] body = payload("DEMO-42", "DEMO", "Bug", "jira:issue_updated", "Done", "Crash <&> fix",
                "private description that must not be sent");

        send(body, "event-1").andExpect(status().isOk());
        send(body, "event-1").andExpect(status().isOk());

        assertEquals(1, telegram.messages.size());
        String message = telegram.messages.getFirst();
        assertTrue(message.contains("status changed to Done"));
        assertTrue(message.contains("Crash &lt;&amp;&gt; fix"));
        assertTrue(message.contains("https://jira.example.test/browse/DEMO-42"));
        assertFalse(message.contains("private description"));
    }

    @Test
    void filtersOtherProjectsIssueTypesStatusesAndUnsupportedEvents() throws Exception {
        send(payload("DEMO-1", "OTHER", "Bug", "jira:issue_updated", "Done", "Summary", ""), "other-project")
                .andExpect(status().isOk());
        send(payload("DEMO-2", "DEMO", "Story", "jira:issue_updated", "Done", "Summary", ""), "other-type")
                .andExpect(status().isOk());
        send(payload("DEMO-3", "DEMO", "Bug", "jira:issue_updated", "To Do", "Summary", ""), "other-status")
                .andExpect(status().isOk());
        send(payload("DEMO-4", "DEMO", "Bug", "comment_created", "Done", "Summary", ""), "other-event")
                .andExpect(status().isOk());

        assertTrue(telegram.messages.isEmpty());
    }

    @Test
    void rejectsInvalidSignatureAndMissingIdentifier() throws Exception {
        byte[] body = payload("DEMO-42", "DEMO", "Bug", "jira:issue_updated", "Done", "Summary", "");
        mockMvc.perform(post("/jira/webhook")
                        .header("X-Hub-Signature", "sha256=" + "0".repeat(64))
                        .header("X-Atlassian-Webhook-Identifier", "event-bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/jira/webhook")
                        .header("X-Hub-Signature", signature(body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        assertTrue(telegram.messages.isEmpty());
    }

    @Test
    void failedTelegramDeliveryCanBeRetriedWithSameJiraEventIdentifier() throws Exception {
        byte[] body = payload("DEMO-42", "DEMO", "Bug", "jira:issue_updated", "Done", "Summary", "");
        telegram.failNext = true;

        send(body, "event-retry").andExpect(status().isServiceUnavailable());
        send(body, "event-retry").andExpect(status().isOk());

        assertEquals(2, telegram.calls);
        assertEquals(1, telegram.messages.size());
    }

    private org.springframework.test.web.servlet.ResultActions send(byte[] body, String identifier) throws Exception {
        return mockMvc.perform(post("/jira/webhook")
                .header("X-Hub-Signature", signature(body))
                .header("X-Atlassian-Webhook-Identifier", identifier)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private byte[] payload(
            String key, String project, String issueType, String event, String status, String summary, String description)
            throws Exception {
        Map<String, Object> fields = Map.of(
                "project", Map.of("key", project),
                "issuetype", Map.of("name", issueType),
                "status", Map.of("name", status),
                "summary", summary,
                "description", description);
        return mapper.writeValueAsBytes(Map.of(
                "webhookEvent", event,
                "issue", Map.of("key", key, "fields", fields),
                "changelog", Map.of("items", List.of(Map.of("field", "status", "toString", status)))));
    }

    private String signature(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class RecordingTelegramClient extends TelegramBotClient {
        private final java.util.ArrayList<String> messages = new java.util.ArrayList<>();
        private int calls;
        private boolean failNext;

        private RecordingTelegramClient() {
            super("", false, "https://api.telegram.org", Duration.ofSeconds(1), new ObjectMapper(),
                    request -> new TransportResponse(200, "{}"));
        }

        @Override
        public void sendHtmlOrLog(String chatId, String html) {
            calls++;
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("synthetic Telegram delivery failure");
            }
            messages.add(html);
        }
    }
}
