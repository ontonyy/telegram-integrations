package example.telegram;

import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TelegramBotClient {
    private final String token;
    private final boolean sendEnabled;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public TelegramBotClient(
            @Value("${telegram.bot-token:}") String token,
            @Value("${telegram.send-enabled:false}") boolean sendEnabled,
            ObjectMapper mapper) {
        this.token = token;
        this.sendEnabled = sendEnabled;
        this.mapper = mapper;
    }

    public void sendOrLog(String chatId, String text) {
        if (!sendEnabled) {
            System.out.printf("DRY RUN: reply would be sent to chat=%s (message content omitted)%n", chatId);
            return;
        }
        if (token.isBlank()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN is required when sends are enabled");
        }
        try {
            String body = mapper.writeValueAsString(Map.of("chat_id", chatId, "text", text));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Telegram Bot API returned HTTP " + response.statusCode());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Telegram send failed", exception);
        }
    }
}
