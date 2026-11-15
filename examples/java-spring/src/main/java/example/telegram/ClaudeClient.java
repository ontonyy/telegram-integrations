package example.telegram;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClaudeClient {
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper;
    private final HttpTransport transport;

    public ClaudeClient(
            @Value("${anthropic.api-key:}") String apiKey,
            @Value("${anthropic.model:}") String model,
            ObjectMapper mapper,
            HttpTransport transport) {
        this.apiKey = apiKey;
        this.model = model;
        this.mapper = mapper;
        this.transport = transport;
    }

    public String answer(String userQuestion) {
        if (apiKey.isBlank() || model.isBlank()) {
            return "AI is not configured. The submitted prompt was not sent to a provider.";
        }
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "max_tokens", 300,
                    "messages", List.of(Map.of("role", "user", "content", userQuestion))));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .timeout(Duration.ofSeconds(30))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            TransportResponse response = transport.send(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "AI provider returned HTTP " + response.statusCode() + ". No automatic retry was made.";
            }
            JsonNode json = mapper.readTree(response.body());
            return json.path("content").path(0).path("text").asText("The provider returned no text response.");
        } catch (Exception exception) {
            return "AI request failed. Check server-side provider configuration.";
        }
    }
}
