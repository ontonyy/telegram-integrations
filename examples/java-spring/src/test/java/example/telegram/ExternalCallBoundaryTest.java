package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ExternalCallBoundaryTest {
    @Test
    void telegramDryRunDoesNotCallTheHttpTransport() {
        AtomicInteger calls = new AtomicInteger();
        HttpTransport transport = request -> {
            calls.incrementAndGet();
            return new TransportResponse(200, "{}");
        };
        TelegramBotClient telegram = new TelegramBotClient("", false, new ObjectMapper(), transport);

        telegram.sendOrLog("12345", "synthetic test message");

        assertEquals(0, calls.get());
    }

    @Test
    void unconfiguredAiDoesNotCallTheHttpTransport() {
        AtomicInteger calls = new AtomicInteger();
        HttpTransport transport = request -> {
            calls.incrementAndGet();
            return new TransportResponse(200, "{}");
        };
        ClaudeClient claude = new ClaudeClient("", "", new ObjectMapper(), transport);

        assertEquals("AI is not configured. The submitted prompt was not sent to a provider.",
                claude.answer("synthetic test prompt"));
        assertEquals(0, calls.get());
    }
}
