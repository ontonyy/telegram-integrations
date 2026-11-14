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

    public TelegramWebhookController(
            @Value("${telegram.webhook-secret:local-demo-secret}") String webhookSecret,
            BotReplyService replyService) {
        this.webhookSecret = webhookSecret;
        this.replyService = replyService;
    }

    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String suppliedSecret,
            @RequestBody JsonNode update) {
        if (suppliedSecret == null || !MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode message = update.path("message");
        String text = message.path("text").asText("");
        String chatType = message.path("chat").path("type").asText("");
        String chatId = message.path("chat").path("id").asText("");
        String userId = message.path("from").path("id").asText("");
        if (!text.isBlank() && !chatId.isBlank() && !userId.isBlank()) {
            Optional<String> reply = replyService.handle(text, chatType, chatId, userId);
            reply.ifPresent(body -> replyService.sendOrLog(chatId, body));
        }
        return ResponseEntity.ok().build();
    }
}
