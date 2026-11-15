package example.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
public class JiraWebhookController {
    private static final int MAX_PAYLOAD_BYTES = 1_000_000;
    private final String webhookSecret;
    private final JiraWebhookSignatureValidator signatures;
    private final JiraWebhookRegistry registry;
    private final JiraIssueNotificationService notifications;
    private final ObjectMapper mapper;

    public JiraWebhookController(
            @Value("${jira.webhook-secret:}") String webhookSecret,
            JiraWebhookSignatureValidator signatures,
            JiraWebhookRegistry registry,
            JiraIssueNotificationService notifications,
            ObjectMapper mapper) {
        this.webhookSecret = webhookSecret;
        this.signatures = signatures;
        this.registry = registry;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    @PostMapping("/jira/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Hub-Signature", required = false) String signature,
            @RequestHeader(value = "X-Atlassian-Webhook-Identifier", required = false) String identifier,
            @RequestBody(required = false) byte[] body) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (body == null || body.length == 0) {
            return ResponseEntity.badRequest().build();
        }
        if (body.length > MAX_PAYLOAD_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        if (!signatures.isValid(body, signature, webhookSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (identifier == null || identifier.isBlank() || identifier.length() > 200) {
            return ResponseEntity.badRequest().build();
        }

        try {
            JsonNode payload = mapper.readTree(body);
            if (!payload.isObject()) {
                return ResponseEntity.badRequest().build();
            }
            String event = payload.path("webhookEvent").asText("");
            if (!"jira:issue_created".equals(event) && !"jira:issue_updated".equals(event)) {
                return ResponseEntity.ok().build();
            }
            registry.processOnce(identifier, () -> notifications.notifyIfSignificant(payload));
            return ResponseEntity.ok().build();
        } catch (JacksonException exception) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
