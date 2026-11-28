package example.telegram;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Optional, read-only Jira issue lookup. It is called only when jira.blocks.enabled is true. */
@Component
public class JiraRestIssueReader implements JiraIssueReader {
    private final String baseUrl;
    private final String authorization;
    private final ObjectMapper mapper;
    private final HttpTransport transport;

    public JiraRestIssueReader(
            @Value("${jira.base-url:https://example.atlassian.net}") String baseUrl,
            @Value("${jira.api-email:}") String email,
            @Value("${jira.api-token:}") String token,
            ObjectMapper mapper,
            HttpTransport transport) {
        URI base = URI.create(baseUrl.strip());
        if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null) {
            throw new IllegalArgumentException("Jira base URL must be an HTTPS origin");
        }
        this.baseUrl = baseUrl.strip().replaceAll("/+$", "");
        this.authorization = email.isBlank() || token.isBlank() ? "" : "Basic " + Base64.getEncoder()
                .encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));
        this.mapper = mapper;
        this.transport = transport;
    }

    @Override
    public JiraIssueDetails read(String issueId) {
        if (issueId == null || !issueId.matches("[0-9]+")) {
            throw new IllegalArgumentException("Jira issue ID must contain only digits");
        }
        if (authorization.isBlank()) {
            throw new IllegalStateException("JIRA_API_EMAIL and JIRA_API_TOKEN are required when Blocks routing is enabled");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/rest/api/3/issue/" + issueId
                            + "?fields=project,labels,status,issuetype"))
                    .timeout(Duration.ofSeconds(10)).header("Authorization", authorization)
                    .header("Accept", "application/json").GET().build();
            TransportResponse response = transport.send(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Jira issue lookup failed with HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode fields = root.path("fields");
            List<String> labels = StreamSupport.stream(fields.path("labels").spliterator(), false)
                    .map(JsonNode::asText).toList();
            return new JiraIssueDetails(root.path("id").asText(issueId), root.path("key").asText(""),
                    fields.path("project").path("key").asText(""), labels,
                    fields.path("status").path("statusCategory").path("key").asText(""),
                    fields.path("issuetype").path("name").asText(""));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Jira issue lookup interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Jira issue lookup failed", exception);
        }
    }
}
