package example.telegram;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TelegramBotClient implements BusinessMessageSender {
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", text);
        sendMethodOrLog("sendMessage", payload);
    }

    @Override
    public void sendBusinessMessageOrLog(String connectionId, String chatId, String text) {
        if (connectionId == null || connectionId.isBlank()) {
            throw new IllegalArgumentException("Business connection ID is required");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("business_connection_id", connectionId);
        payload.put("chat_id", chatId);
        payload.put("text", text);
        sendMethodOrLog("sendMessage", payload);
    }

    /** Sends HTML parse mode; callers must escape any dynamic values inserted into the HTML. */
    public void sendHtmlOrLog(String chatId, String html) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", html);
        payload.put("parse_mode", "HTML");
        sendMethodOrLog("sendMessage", payload);
    }

    public long sendHtmlAndGetMessageId(String chatId, String html) {
        if (!sendEnabled) {
            System.out.println("DRY RUN: Telegram delivery is disabled (message and destination omitted)");
            return 0;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", html);
        payload.put("parse_mode", "HTML");
        JsonNode response = sendMethod("sendMessage", payload);
        return response.path("result").path("message_id").asLong(0);
    }

    public void editHtmlOrLog(String chatId, long messageId, String html) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId);
        payload.put("message_id", messageId);
        payload.put("text", html);
        payload.put("parse_mode", "HTML");
        sendMethodOrLog("editMessageText", payload);
    }

    public void sendMessageWithInlineKeyboardOrLog(
            String chatId, String text, List<List<InlineButton>> keyboard) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", text);
        payload.put("reply_markup", Map.of("inline_keyboard", serializeKeyboard(keyboard)));
        sendMethodOrLog("sendMessage", payload);
    }

    public void editMessageWithInlineKeyboardOrLog(
            String chatId, long messageId, String text, List<List<InlineButton>> keyboard) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId);
        payload.put("message_id", messageId);
        payload.put("text", text);
        payload.put("reply_markup", Map.of("inline_keyboard", serializeKeyboard(keyboard)));
        sendMethodOrLog("editMessageText", payload);
    }

    public void answerCallbackQueryOrLog(String callbackQueryId, String text) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("callback_query_id", callbackQueryId);
        payload.put("text", text);
        sendMethodOrLog("answerCallbackQuery", payload);
    }

    private void sendMethodOrLog(String method, Map<String, Object> payload) {
        if (!sendEnabled) {
            System.out.println("DRY RUN: Telegram delivery is disabled (message and destination omitted)");
            return;
        }
        if (token.isBlank()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN is required when sends are enabled");
        }
        try {
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl.replaceAll("/+$", "") + "/bot" + token + "/" + method))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            TransportResponse response = transport.send(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TelegramBotApiException(response.statusCode(), readRetryAfter(response.body()));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telegram send was interrupted", exception);
        } catch (TelegramBotApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Telegram send failed", exception);
        }
    }

    private JsonNode sendMethod(String method, Map<String, Object> payload) {
        if (!sendEnabled) throw new IllegalStateException("Telegram delivery is disabled");
        if (token.isBlank()) throw new IllegalStateException("TELEGRAM_BOT_TOKEN is required when sends are enabled");
        try {
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl.replaceAll("/+$", "") + "/bot" + token + "/" + method))
                    .timeout(requestTimeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            TransportResponse response = transport.send(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TelegramBotApiException(response.statusCode(), readRetryAfter(response.body()));
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telegram send was interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Telegram send failed", exception);
        }
    }

    public NotificationDeliveryResult sendForOutbox(String chatId, String text) {
        if (!sendEnabled) {
            throw new IllegalStateException("Telegram delivery is disabled; outbox worker must remain disabled");
        }
        if (token.isBlank()) {
            return NotificationDeliveryResult.permanent();
        }
        try {
            sendOrLog(chatId, text);
            return NotificationDeliveryResult.sent();
        } catch (TelegramBotApiException exception) {
            if (exception.statusCode() == 429 || exception.statusCode() >= 500) {
                return NotificationDeliveryResult.retryable(exception.retryAfter());
            }
            return NotificationDeliveryResult.permanent();
        } catch (IllegalStateException ambiguousTransportFailure) {
            return NotificationDeliveryResult.retryable(java.time.Duration.ZERO);
        }
    }

    private java.time.Duration readRetryAfter(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            long seconds = root.path("parameters").path("retry_after").asLong(0);
            return seconds > 0 ? java.time.Duration.ofSeconds(seconds) : java.time.Duration.ZERO;
        } catch (Exception ignored) {
            return java.time.Duration.ZERO;
        }
    }

    private List<List<Map<String, String>>> serializeKeyboard(List<List<InlineButton>> keyboard) {
        return keyboard.stream()
                .map(row -> row.stream()
                        .map(button -> Map.of("text", button.text(), "callback_data", button.callbackData()))
                        .toList())
                .toList();
    }

    public record InlineButton(String text, String callbackData) {}
}
