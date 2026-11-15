package example.telegram;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class BotApiTelegramFileGateway implements TelegramFileGateway {
    private final String token;
    private final boolean downloadEnabled;
    private final String apiBaseUrl;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;
    private final HttpTransport jsonTransport;
    private final FileDownloadTransport fileTransport;

    public BotApiTelegramFileGateway(
            @Value("${telegram.bot-token:}") String token,
            @Value("${telegram.file-download-enabled:false}") boolean downloadEnabled,
            @Value("${telegram.api-base-url:https://api.telegram.org}") String apiBaseUrl,
            @Value("${telegram.request-timeout:PT10S}") Duration requestTimeout,
            ObjectMapper mapper,
            HttpTransport jsonTransport,
            FileDownloadTransport fileTransport) {
        this.token = token;
        this.downloadEnabled = downloadEnabled;
        this.apiBaseUrl = apiBaseUrl.replaceAll("/+$", "");
        this.requestTimeout = requestTimeout;
        this.mapper = mapper;
        this.jsonTransport = jsonTransport;
        this.fileTransport = fileTransport;
    }

    @Override
    public DownloadInfo getFile(String fileId) throws IOException, InterruptedException {
        ensureEnabled();
        if (fileId == null || fileId.isBlank() || fileId.length() > 512
                || !fileId.matches("[-_A-Za-z0-9:]+")) {
            throw new IOException("Invalid Telegram file identifier");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/bot" + token + "/getFile"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("file_id", fileId))))
                    .build();
            TransportResponse response = jsonTransport.send(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Telegram getFile returned HTTP " + response.statusCode());
            }
            JsonNode body = mapper.readTree(response.body());
            JsonNode result = body.path("result");
            String filePath = result.path("file_path").asText("");
            JsonNode sizeNode = result.path("file_size");
            long fileSize = sizeNode.isIntegralNumber() && sizeNode.canConvertToLong() ? sizeNode.asLong() : -1;
            if (!body.path("ok").asBoolean(false) || !validFilePath(filePath)) {
                throw new IOException("Telegram did not return a valid file path");
            }
            return new DownloadInfo(filePath, fileSize);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Telegram file metadata request failed");
        }
    }

    @Override
    public void download(String filePath, java.nio.file.Path destination, long maxBytes)
            throws IOException, InterruptedException {
        ensureEnabled();
        if (!validFilePath(filePath)) {
            throw new IOException("Invalid Telegram file path");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/file/bot" + token + "/" + filePath))
                .timeout(requestTimeout)
                .GET()
                .build();
        fileTransport.download(request, destination, maxBytes);
    }

    private boolean validFilePath(String filePath) {
        if (filePath == null || filePath.isBlank() || filePath.length() > 512
                || !filePath.matches("[A-Za-z0-9_./-]+")) {
            return false;
        }
        for (String segment : filePath.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private void ensureEnabled() throws IOException {
        if (!downloadEnabled || token.isBlank()) {
            throw new IOException("Telegram file downloads are not enabled");
        }
    }
}
