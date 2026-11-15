package example.telegram;

import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TelegramBotClient {
    private final String token;
    private final boolean sendEnabled;
    private final String apiBaseUrl;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;
    private final HttpTransport transport;

    public TelegramBotClient(
            @Value("${telegram.bot-token:}") String token,
            @Value("${telegram.send-enabled:false}") boolean sendEnabled,
            @Value("${telegram.api-base-url:https://api.telegram.org}") String apiBaseUrl,
            @Value("${telegram.request-timeout:PT10S}") Duration requestTimeout,
            ObjectMapper mapper,
            HttpTransport transport) {
        this.token = token;
        this.sendEnabled = sendEnabled;
        this.apiBaseUrl = apiBaseUrl;
        this.requestTimeout = requestTimeout;
        this.mapper = mapper;
        this.transport = transport;
    }

    public void sendOrLog(String chatId, String text) {
        sendOrLog(chatId, text, null);
    }

    /** Sends HTML parse mode; callers must escape any dynamic values inserted into the HTML. */
    public void sendHtmlOrLog(String chatId, String html) {
        sendOrLog(chatId, html, "HTML");
    }

    private void sendOrLog(String chatId, String text, String parseMode) {
        if (!sendEnabled) {
            System.out.println("DRY RUN: Telegram delivery is disabled (message and destination omitted)");
            return;
        }
        if (token.isBlank()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN is required when sends are enabled");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", text);
            if (parseMode != null) {
                payload.put("parse_mode", parseMode);
            }
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl.replaceAll("/+$", "") + "/bot" + token + "/sendMessage"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            TransportResponse response = transport.send(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Telegram Bot API returned HTTP " + response.statusCode());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Telegram send failed", exception);
        }
    }
}
