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
    private final GroupVoteService groupVoteService;
    private final GrafanaAlertService grafanaAlertService;
    private final FileIntakeService fileIntakeService;

    public TelegramWebhookController(
            @Value("${telegram.webhook-secret:}") String webhookSecret,
            BotReplyService replyService,
            TelegramUpdateRegistry updateRegistry,
            GroupVoteService groupVoteService,
            GrafanaAlertService grafanaAlertService,
            FileIntakeService fileIntakeService) {
        this.webhookSecret = webhookSecret;
        this.replyService = replyService;
        this.updateRegistry = updateRegistry;
        this.groupVoteService = groupVoteService;
        this.grafanaAlertService = grafanaAlertService;
        this.fileIntakeService = fileIntakeService;
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
            JsonNode callback = update.path("callback_query");
            if (callback.isObject()) {
                return handleCallback(updateIdNode.asLong(), callback);
            }
            return ResponseEntity.ok().build();
        }
        String text = message.path("text").asText("");
        String chatType = message.path("chat").path("type").asText("");
        String chatId = message.path("chat").path("id").asText("");
        String userId = message.path("from").path("id").asText("");
        if (chatId.isBlank() || userId.isBlank()) {
            return ResponseEntity.ok().build();
        }

        try {
            long updateId = updateIdNode.asLong();
            JsonNode document = message.path("document");
            if (document.isObject()) {
                if (!"private".equals(chatType)) {
                    return ResponseEntity.ok().build();
                }
                updateRegistry.processOnce(updateId, () -> {
                    if (!replyService.hasFileConsent(userId)) {
                        replyService.sendOrLog(chatId,
                                "File intake is off until you read /file-help and send /file-consent in this private chat.");
                        return;
                    }
                    JsonNode size = document.path("file_size");
                    long fileSize = size.isIntegralNumber() && size.canConvertToLong() ? size.asLong() : -1;
                    FileIntakeService.Result result = fileIntakeService.process(new FileIntakeService.DocumentMetadata(
                            document.path("file_id").asText(""),
                            document.path("file_name").asText(""),
                            document.path("mime_type").asText(""),
                            fileSize));
                    replyService.sendOrLog(chatId, result.reply());
                });
                return ResponseEntity.ok().build();
            }
            if (text.isBlank()) {
                return ResponseEntity.ok().build();
            }
            String command = text.strip().split("\\s", 2)[0].split("@", 2)[0];
            if ("/vote".equals(command) && isGroup(chatType)) {
                updateRegistry.processOnce(updateId, () -> {
                    GroupVoteService.VotePrompt prompt = groupVoteService.start(chatId, userId);
                    replyService.sendVotePrompt(chatId, prompt.text(), prompt.keyboard());
                });
                return ResponseEntity.ok().build();
            }
            if ("/closevote".equals(command) && isGroup(chatType)) {
                String[] parts = text.strip().split("\\s+", 2);
                if (parts.length == 2) {
                    updateRegistry.processOnce(updateId, () -> {
                        GroupVoteService.CloseResult result = groupVoteService.close(chatId, userId, parts[1]);
                        if (result.authorized()) {
                            replyService.sendOrLog(chatId, result.finalMessage());
                        }
                    });
                }
                return ResponseEntity.ok().build();
            }
            updateRegistry.processOnce(updateId, () -> {
                Optional<String> reply = replyService.handle(text, chatType, chatId, userId);
                reply.ifPresent(body -> replyService.sendOrLog(chatId, body));
            });
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        return ResponseEntity.ok().build();
    }

    private ResponseEntity<Void> handleCallback(long updateId, JsonNode callback) {
        String callbackId = callback.path("id").asText("");
        String userId = callback.path("from").path("id").asText("");
        JsonNode message = callback.path("message");
        String chatId = message.path("chat").path("id").asText("");
        String chatType = message.path("chat").path("type").asText("");
        JsonNode messageIdNode = message.path("message_id");
        String data = callback.path("data").asText("");
        if (callbackId.isBlank() || userId.isBlank() || chatId.isBlank() || chatType.isBlank()
                || !messageIdNode.canConvertToLong() || data.isBlank()) {
            return ResponseEntity.ok().build();
        }

        try {
            long messageId = messageIdNode.asLong();
            if (data.startsWith("grafana_ack|")) {
                String actionToken = data.substring("grafana_ack|".length());
                boolean processed = updateRegistry.processOnce(updateId, () -> {
                    GrafanaAlertService.Acknowledgement acknowledgement =
                            grafanaAlertService.acknowledge(chatId, userId, actionToken);
                    replyService.answerCallback(callbackId, acknowledgement.callbackText());
                    if (acknowledgement.updatedMessage() != null) {
                        replyService.editVoteMessage(chatId, messageId, acknowledgement.updatedMessage(),
                                java.util.List.of());
                    }
                });
                if (!processed) {
                    replyService.answerCallback(callbackId, "This update was already processed.");
                }
                return ResponseEntity.ok().build();
            }
            boolean processed = updateRegistry.processOnce(updateId, () -> {
                GroupVoteService.VoteResult result = groupVoteService.cast(chatId, userId, data);
                replyService.answerCallback(callbackId, result.callbackText());
                if (result.accepted()) {
                    replyService.editVoteMessage(chatId, messageId, result.updatedMessage(), result.keyboard());
                }
            });
            if (!processed) {
                replyService.answerCallback(callbackId, "This update was already processed.");
            }
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok().build();
    }

    private boolean isGroup(String chatType) {
        return "group".equals(chatType) || "supergroup".equals(chatType);
    }
}
