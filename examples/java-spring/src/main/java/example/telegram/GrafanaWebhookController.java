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
public class GrafanaWebhookController {
    private static final int MAX_PAYLOAD_BYTES = 1_000_000;
    private final String webhookSecret;
    private final String chatId;
    private final GrafanaWebhookSignatureValidator signatures;
    private final GrafanaAlertService alerts;
    private final ObjectMapper mapper;

    public GrafanaWebhookController(
            @Value("${grafana.webhook-secret:}") String webhookSecret,
            @Value("${grafana.telegram-chat-id:}") String chatId,
            GrafanaWebhookSignatureValidator signatures,
            GrafanaAlertService alerts,
            ObjectMapper mapper) {
        this.webhookSecret = webhookSecret;
        this.chatId = chatId;
        this.signatures = signatures;
        this.alerts = alerts;
        this.mapper = mapper;
    }

    @PostMapping("/grafana/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Grafana-Alerting-Signature", required = false) String signature,
            @RequestHeader(value = "X-Grafana-Timestamp", required = false) String timestamp,
            @RequestBody(required = false) byte[] body) {
        if (webhookSecret == null || webhookSecret.isBlank() || chatId == null || chatId.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (body == null || body.length == 0) {
            return ResponseEntity.badRequest().build();
        }
        if (body.length > MAX_PAYLOAD_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        if (!signatures.isValid(body, signature, timestamp, webhookSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            JsonNode payload = mapper.readTree(body);
            if (!payload.isObject() || !payload.path("alerts").isArray()) {
                return ResponseEntity.badRequest().build();
            }
            alerts.receive(payload);
            return ResponseEntity.ok().build();
        } catch (JacksonException exception) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
