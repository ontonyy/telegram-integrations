package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AiConsentFlowTest {
    private static final String USER = "1001";
    private final CapturingClaudeClient claude = new CapturingClaudeClient();
    private final BotReplyService replies = new BotReplyService(telegram(), claude);

    @Test
    void requiresDisclosureAndConsentAndConfirmsEachQuestionBeforeProviderCall() {
        assertContains(replies.handle("/consent", "private", "1001", USER).orElseThrow(), "/ai-help");
        replies.handle("/ai-help", "private", "1001", USER);
        assertContains(replies.handle("/consent", "private", "1001", USER).orElseThrow(), "recorded");

        String prompt = "How should this demo validate input?";
        assertContains(replies.handle("/ask " + prompt, "private", "1001", USER).orElseThrow(), "/ai-confirm");
        assertContains(replies.handle("/ask replace the question", "private", "1001", USER).orElseThrow(), "already pending");
        assertEquals(0, claude.calls.get());

        assertEquals("Synthetic answer", replies.handle("/ai-confirm", "private", "1001", USER).orElseThrow());
        assertEquals(prompt, claude.lastQuestion);
        assertEquals(1, claude.calls.get());
        assertContains(replies.handle("/ai-confirm", "private", "1001", USER).orElseThrow(), "No pending");
        assertEquals(1, claude.calls.get());
    }

    @Test
    void groupQuestionsAndConfirmationsNeverReachTheProvider() {
        replies.handle("/ai-help", "private", "1001", USER);
        replies.handle("/consent", "private", "1001", USER);

        assertContains(replies.handle("/ask group question", "group", "-10", USER).orElseThrow(), "disabled in groups");
        replies.handle("/ask pending private question", "private", "1001", USER);
        assertContains(replies.handle("/ai-confirm", "group", "-10", USER).orElseThrow(), "private chat");
        assertEquals(0, claude.calls.get());
    }

    @Test
    void cancellationAndRevocationClearPendingQuestionAndConsent() {
        replies.handle("/ai-help", "private", "1001", USER);
        replies.handle("/consent", "private", "1001", USER);
        replies.handle("/ask cancel me", "private", "1001", USER);
        assertContains(replies.handle("/ai-cancel", "private", "1001", USER).orElseThrow(), "cleared");
        assertContains(replies.handle("/ai-confirm", "private", "1001", USER).orElseThrow(), "No pending");

        replies.handle("/ask revoke me", "private", "1001", USER);
        replies.handle("/stop", "private", "1001", USER);
        assertContains(replies.handle("/ai-confirm", "private", "1001", USER).orElseThrow(), "consent");
        assertContains(replies.handle("/ask after revoke", "private", "1001", USER).orElseThrow(), "/consent");
        assertEquals(0, claude.calls.get());
    }

    @Test
    void humanHandoffMarksFollowUpWithoutCallingAi() {
        assertContains(replies.handle("/human", "private", "1001", USER).orElseThrow(), "No operator/helpdesk queue");
        assertTrue(replies.hasHumanHandoffRequest(USER));
        assertEquals(0, claude.calls.get());
    }

    @Test
    void providerErrorsAreGenericAndDoNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        ClaudeClient client = new ClaudeClient("synthetic-key", "synthetic-model", new ObjectMapper(), request -> {
            calls.incrementAndGet();
            return new TransportResponse(503, "private provider detail");
        });

        String answer = client.answer("synthetic question");

        assertEquals("AI provider returned HTTP 503. No automatic retry was made.", answer);
        assertEquals(1, calls.get());
        assertFalse(answer.contains("private provider detail"));
    }

    private static void assertContains(String value, String expected) {
        assertTrue(value.contains(expected), () -> "Expected '" + value + "' to contain '" + expected + "'");
    }

    private static TelegramBotClient telegram() {
        return new TelegramBotClient("", false, "https://api.telegram.org", java.time.Duration.ofSeconds(1),
                new ObjectMapper(), request -> new TransportResponse(200, "{}"));
    }

    private static final class CapturingClaudeClient extends ClaudeClient {
        private final AtomicInteger calls = new AtomicInteger();
        private String lastQuestion;

        private CapturingClaudeClient() {
            super("", "", new ObjectMapper(), request -> new TransportResponse(200, "{}"));
        }

        @Override
        public String answer(String userQuestion) {
            calls.incrementAndGet();
            lastQuestion = userQuestion;
            return "Synthetic answer";
        }
    }
}
