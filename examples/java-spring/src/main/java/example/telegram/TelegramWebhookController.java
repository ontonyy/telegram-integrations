package example.telegram;

import tools.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TelegramWebhookController {
    private final String webhookSecret;
    private final BotReplyService replyService;
    private final TelegramUpdateRegistry updateRegistry;

    public TelegramWebhookController(
            @Value("${telegram.webhook-secret:}") String webhookSecret,
            BotReplyService replyService,
            TelegramUpdateRegistry updateRegistry) {
        this.webhookSecret = webhookSecret;
        this.replyService = replyService;
        this.updateRegistry = updateRegistry;
    }

    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String suppliedSecret,
            @RequestBody JsonNode update) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (suppliedSecret == null || !MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode updateIdNode = update.path("update_id");
        if (!update.isObject()
                || !updateIdNode.isIntegralNumber()
                || !updateIdNode.canConvertToLong()
                || updateIdNode.asLong() < 0) {
            return ResponseEntity.badRequest().build();
        }

        JsonNode message = update.path("message");
        if (!message.isObject()) {
            return ResponseEntity.ok().build();
        }
        String text = message.path("text").asText("");
        String chatType = message.path("chat").path("type").asText("");
        String chatId = message.path("chat").path("id").asText("");
        String userId = message.path("from").path("id").asText("");
        if (text.isBlank() || chatId.isBlank() || userId.isBlank()) {
            return ResponseEntity.ok().build();
        }

        try {
            long updateId = updateIdNode.asLong();
            updateRegistry.processOnce(updateId, () -> {
                Optional<String> reply = replyService.handle(text, chatType, chatId, userId);
                reply.ifPresent(body -> replyService.sendOrLog(chatId, body));
            });
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        return ResponseEntity.ok().build();
    }
}
