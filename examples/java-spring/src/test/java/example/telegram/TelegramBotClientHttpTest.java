package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class TelegramBotClientHttpTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private ExecutorService serverExecutor;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void sendsExpectedJsonToLocalMockServer() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeResponse(exchange, 200, "{\"ok\":true,\"result\":{}}");
        });

        client("synthetic-token", Duration.ofSeconds(2)).sendOrLog("12345", "synthetic build passed");

        assertEquals("POST", method.get());
        assertEquals("/botsynthetic-token/sendMessage", path.get());
        assertTrue(contentType.get().startsWith("application/json"));
        JsonNode sent = mapper.readTree(requestBody.get());
        assertEquals("12345", sent.path("chat_id").asText());
        assertEquals("synthetic build passed", sent.path("text").asText());
    }

    @Test
    void classifiesFloodControlAndUsesTelegramRetryAfter() throws Exception {
        startServer(exchange -> writeResponse(exchange, 429,
                "{\"ok\":false,\"error_code\":429,\"parameters\":{\"retry_after\":17}}"));

        NotificationDeliveryResult result = client("synthetic-token", Duration.ofSeconds(2))
                .sendForOutbox("12345", "synthetic retry test");

        assertEquals(NotificationDeliveryResult.Kind.RETRYABLE_FAILURE, result.kind());
        assertEquals(Duration.ofSeconds(17), result.retryAfter());
    }

    @Test
    void classifiesClientErrorsAsPermanentForOutbox() throws Exception {
        startServer(exchange -> writeResponse(exchange, 400,
                "{\"ok\":false,\"error_code\":400,\"description\":\"synthetic invalid chat\"}"));

        NotificationDeliveryResult result = client("synthetic-token", Duration.ofSeconds(2))
                .sendForOutbox("12345", "synthetic permanent test");

        assertEquals(NotificationDeliveryResult.Kind.PERMANENT_FAILURE, result.kind());
    }

    @Test
    void sendsHtmlParseModeOnlyForHtmlMessages() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeResponse(exchange, 200, "{\"ok\":true,\"result\":{}}");
        });

        client("synthetic-token", Duration.ofSeconds(2))
                .sendHtmlOrLog("12345", "<b>CI Failure</b>");

        JsonNode sent = mapper.readTree(requestBody.get());
        assertEquals("HTML", sent.path("parse_mode").asText());
        assertEquals("<b>CI Failure</b>", sent.path("text").asText());
    }

    @Test
    void sendsBusinessReplyWithConnectionIdAndChatReference() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeResponse(exchange, 200, "{\"ok\":true,\"result\":{}}");
        });

        client("synthetic-token", Duration.ofSeconds(2))
                .sendBusinessMessageOrLog("business-connection-demo", "555", "Reviewed response");

        JsonNode sent = mapper.readTree(requestBody.get());
        assertEquals("business-connection-demo", sent.path("business_connection_id").asText());
        assertEquals("555", sent.path("chat_id").asText());
        assertEquals("Reviewed response", sent.path("text").asText());
    }

    @Test
    void sendsVoteKeyboardAndCallbackRequestsToExpectedBotApiMethods() throws Exception {
        List<String> paths = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        startServer(exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeResponse(exchange, 200, "{\"ok\":true,\"result\":{}}");
        });
        TelegramBotClient client = client("synthetic-token", Duration.ofSeconds(2));

        client.sendMessageWithInlineKeyboardOrLog("-123", "Choose a time", List.of(List.of(
                new TelegramBotClient.InlineButton("Tuesday", "vote|abc123|0"))));
        client.answerCallbackQueryOrLog("callback-1", "Vote recorded");
        client.editMessageWithInlineKeyboardOrLog("-123", 55, "Live result", List.of());

        assertEquals(List.of(
                "/botsynthetic-token/sendMessage",
                "/botsynthetic-token/answerCallbackQuery",
                "/botsynthetic-token/editMessageText"), paths);
        JsonNode send = mapper.readTree(bodies.get(0));
        assertEquals("-123", send.path("chat_id").asText());
        assertEquals("Tuesday", send.path("reply_markup").path("inline_keyboard").get(0).get(0)
                .path("text").asText());
        assertEquals("vote|abc123|0", send.path("reply_markup").path("inline_keyboard").get(0).get(0)
                .path("callback_data").asText());
        assertEquals("callback-1", mapper.readTree(bodies.get(1)).path("callback_query_id").asText());
        JsonNode edit = mapper.readTree(bodies.get(2));
        assertEquals(55, edit.path("message_id").asInt());
        assertEquals("Live result", edit.path("text").asText());
    }

    @Test
    void doesNotExposeTokenOrResponseBodyOnHttpFailure() {
        startServer(exchange -> writeResponse(exchange, 429, "synthetic private response body"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> client("synthetic-token", Duration.ofSeconds(2))
                        .sendOrLog("12345", "synthetic private message"));

        assertFalse(failure.toString().contains("synthetic-token"));
        assertFalse(failure.toString().contains("synthetic private response body"));
        assertTrue(failure instanceof TelegramBotApiException);
        assertEquals(429, ((TelegramBotApiException) failure).statusCode());
        assertFalse(failure.getMessage().contains("synthetic private response body"));
    }

    @Test
    void timesOutAgainstDelayedLocalMockServerWithoutLeakingRequestData() {
        startServer(exchange -> {
            try {
                Thread.sleep(500);
                writeResponse(exchange, 200, "{\"ok\":true}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client closes the local connection after its configured timeout.
            }
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> client("synthetic-token", Duration.ofMillis(50))
                        .sendOrLog("12345", "synthetic private message"));

        assertEquals("Telegram send failed", failure.getMessage());
        assertFalse(failure.toString().contains("synthetic-token"));
        assertFalse(failure.toString().contains("synthetic private message"));
    }

    private TelegramBotClient client(String token, Duration timeout) {
        return new TelegramBotClient(token, true, serverBaseUrl(), timeout, mapper, new JdkHttpTransport());
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start local HTTP mock", exception);
        }
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.createContext("/", handler);
        server.start();
    }

    private String serverBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
