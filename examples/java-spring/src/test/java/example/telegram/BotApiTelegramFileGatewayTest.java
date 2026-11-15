package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class BotApiTelegramFileGatewayTest {
    @TempDir
    Path tempDir;
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void followsGetFileThenBoundedFileDownloadFlowAgainstLocalServer() throws Exception {
        byte[] fileContents = "synthetic log file".getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> metadataMethod = new AtomicReference<>();
        AtomicReference<String> metadataPath = new AtomicReference<>();
        AtomicReference<String> metadataBody = new AtomicReference<>();
        AtomicReference<String> downloadMethod = new AtomicReference<>();
        AtomicReference<String> downloadPath = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bot", exchange -> {
            metadataMethod.set(exchange.getRequestMethod());
            metadataPath.set(exchange.getRequestURI().getPath());
            metadataBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeResponse(exchange, 200, "{\"ok\":true,\"result\":{\"file_path\":\"documents/synthetic.log\",\"file_size\":"
                    + fileContents.length + "}}");
        });
        server.createContext("/file", exchange -> {
            downloadMethod.set(exchange.getRequestMethod());
            downloadPath.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, fileContents.length);
            exchange.getResponseBody().write(fileContents);
            exchange.close();
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        BotApiTelegramFileGateway gateway = new BotApiTelegramFileGateway(
                "synthetic-token", true, baseUrl, Duration.ofSeconds(2), new ObjectMapper(),
                new JdkHttpTransport(), new JdkFileDownloadTransport());
        TelegramFileGateway.DownloadInfo info = gateway.getFile("synthetic-file-id");
        Path destination = Files.createTempFile(tempDir, "upload-", ".log");
        gateway.download(info.filePath(), destination, 128);

        assertEquals("POST", metadataMethod.get());
        assertEquals("/botsynthetic-token/getFile", metadataPath.get());
        assertEquals("synthetic-file-id", new ObjectMapper().readTree(metadataBody.get()).path("file_id").asText());
        assertEquals("GET", downloadMethod.get());
        assertEquals("/file/botsynthetic-token/documents/synthetic.log", downloadPath.get());
        assertEquals(fileContents.length, info.fileSize());
        assertEquals("synthetic log file", Files.readString(destination));
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
