package example.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Receives Chatwoot events; Chatwoot, not this controller, owns Telegram's webhook. */
@RestController
public class ChatwootWebhookController {
    private final String webhookSecret;
    private final int maxBodyBytes;
    private final ChatwootWebhookProcessor processor;

    public ChatwootWebhookController(
            @Value("${chatwoot.webhook-secret:}") String webhookSecret,
            @Value("${chatwoot.webhook.max-body-bytes:128000}") int maxBodyBytes,
            ChatwootWebhookProcessor processor) {
        if (maxBodyBytes < 1) {
            throw new IllegalArgumentException("Chatwoot webhook body limit must be positive");
        }
        this.webhookSecret = webhookSecret;
        this.maxBodyBytes = maxBodyBytes;
        this.processor = processor;
    }

    @PostMapping("/chatwoot/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Chatwoot-Signature", required = false) String signature,
            @RequestHeader(value = "X-Chatwoot-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Chatwoot-Delivery", required = false) String deliveryId,
            @RequestBody(required = false) byte[] rawBody) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (rawBody == null || rawBody.length == 0) {
            return ResponseEntity.badRequest().build();
        }
        if (rawBody.length > maxBodyBytes) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        // X-Chatwoot-Delivery is optional correlation metadata; the event key comes from signed raw-body JSON.
        ChatwootWebhookProcessor.Outcome outcome = processor.process(rawBody, timestamp, signature, webhookSecret);
        return switch (outcome) {
            case ACCEPTED, DUPLICATE, IGNORED -> ResponseEntity.ok().build();
            case INVALID_SIGNATURE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            case INVALID_PAYLOAD -> ResponseEntity.badRequest().build();
            case RETRYABLE_FAILURE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        };
    }
}
