package example.telegram;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class GrafanaAlertService {
    private static final int MAX_STORED_ALERTS = 1_000;
    private static final int MAX_ALERTS_PER_WEBHOOK = 10;
    private final BotReplyService replies;
    private final String chatId;
    private final Set<String> allowedAcknowledgers;
    private final Map<String, AlertRecord> alertsByFingerprint = new LinkedHashMap<>();
    private final Map<String, String> fingerprintByActionToken = new LinkedHashMap<>();

    public GrafanaAlertService(
            BotReplyService replies,
            @Value("${grafana.telegram-chat-id:}") String chatId,
            @Value("${grafana.ack-user-ids:}") String ackUserIds) {
        this.replies = replies;
        this.chatId = chatId;
        this.allowedAcknowledgers = Arrays.stream(ackUserIds.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public synchronized void receive(JsonNode payload) {
        if (chatId.isBlank() || !payload.path("alerts").isArray()) {
            return;
        }
        int processed = 0;
        for (JsonNode alert : payload.path("alerts")) {
            if (processed >= MAX_ALERTS_PER_WEBHOOK) {
                break;
            }
            String status = alert.path("status").asText(payload.path("status").asText(""));
            String fingerprint = alert.path("fingerprint").asText("");
            String startsAt = alert.path("startsAt").asText("");
            if ((!"firing".equals(status) && !"resolved".equals(status))
                    || !fingerprint.matches("[A-Za-z0-9_-]{1,128}") || startsAt.isBlank()) {
                continue;
            }

            AlertRecord record = recordFor(fingerprint, startsAt);
            record.status = status;
            String alertName = boundedText(alert.path("labels").path("alertname").asText("Unnamed alert"), 120);
            String severity = boundedText(alert.path("labels").path("severity").asText("unspecified"), 40);
            String selectedLabels = selectedLabels(alert.path("labels"));
            String source = safeSource(alert.path("generatorURL").asText(""));
            String text = ("firing".equals(status) ? "🚨 FIRING" : "✅ RESOLVED") + ": " + alertName
                    + "\nSeverity: " + severity + "\nState: " + status
                    + (selectedLabels.isBlank() ? "" : "\nLabels: " + selectedLabels)
                    + "\nFingerprint: " + fingerprint
                    + (source.isBlank() ? "" : "\nSource: " + source);
            if (record.acknowledgedBy != null) {
                text += "\nApplication acknowledged by " + record.acknowledgedBy + " at " + record.acknowledgedAt
                        + " (Grafana alert state unchanged).";
            }
            record.messageText = text;
            List<List<TelegramBotClient.InlineButton>> keyboard = "firing".equals(status)
                    && record.acknowledgedBy == null
                    ? List.of(List.of(new TelegramBotClient.InlineButton("Acknowledge", "grafana_ack|" + record.actionToken)))
                    : List.of();
            replies.sendAlert(chatId, text, keyboard);
            processed++;
        }
    }

    public synchronized Acknowledgement acknowledge(String callbackChatId, String userId, String actionToken) {
        if (!allowedAcknowledgers.contains(userId)) {
            return Acknowledgement.denied("You are not allowed to acknowledge this alert.");
        }
        String fingerprint = fingerprintByActionToken.get(actionToken);
        AlertRecord record = fingerprint == null ? null : alertsByFingerprint.get(fingerprint);
        if (record == null || !record.chatId.equals(callbackChatId)) {
            return Acknowledgement.denied("This alert action is no longer available.");
        }
        if (!"firing".equals(record.status)) {
            return Acknowledgement.denied("This alert is not firing.");
        }
        if (record.acknowledgedBy != null) {
            return new Acknowledgement(false, "Already acknowledged by " + record.acknowledgedBy + ".", null);
        }
        record.acknowledgedBy = userId;
        record.acknowledgedAt = Instant.now().toString();
        record.messageText += "\nApplication acknowledged by " + userId + " at " + record.acknowledgedAt
                + " (application acknowledgement only; Grafana alert state is unchanged).";
        return new Acknowledgement(true, "Acknowledged in this application.", record.messageText);
    }

    private AlertRecord recordFor(String fingerprint, String startsAt) {
        AlertRecord record = alertsByFingerprint.get(fingerprint);
        if (record == null || !record.startsAt.equals(startsAt)) {
            if (record != null) {
                fingerprintByActionToken.remove(record.actionToken);
            }
            record = new AlertRecord(chatId, startsAt, UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            alertsByFingerprint.put(fingerprint, record);
            fingerprintByActionToken.put(record.actionToken, fingerprint);
            trimOldestAlertIfNeeded();
        }
        return record;
    }

    private void trimOldestAlertIfNeeded() {
        if (alertsByFingerprint.size() > MAX_STORED_ALERTS) {
            String oldestFingerprint = alertsByFingerprint.keySet().iterator().next();
            AlertRecord removed = alertsByFingerprint.remove(oldestFingerprint);
            fingerprintByActionToken.remove(removed.actionToken);
        }
    }

    private String safeSource(String value) {
        if (value.isBlank() || value.length() > 500) {
            return "";
        }
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null ? uri.toString() : "";
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private String selectedLabels(JsonNode labels) {
        return List.of("team", "service", "environment").stream()
                .map(key -> Map.entry(key, labels.path(key).asText("")))
                .filter(entry -> !entry.getValue().isBlank())
                .map(entry -> entry.getKey() + "=" + boundedText(entry.getValue(), 80))
                .collect(Collectors.joining(", "));
    }

    private String boundedText(String value, int limit) {
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").strip();
        return compact.length() > limit ? compact.substring(0, limit - 3) + "..." : compact;
    }

    public record Acknowledgement(boolean changed, String callbackText, String updatedMessage) {
        private static Acknowledgement denied(String text) {
            return new Acknowledgement(false, text, null);
        }
    }

    private static final class AlertRecord {
        private final String chatId;
        private final String startsAt;
        private final String actionToken;
        private String status;
        private String messageText;
        private String acknowledgedBy;
        private String acknowledgedAt;

        private AlertRecord(String chatId, String startsAt, String actionToken) {
            this.chatId = chatId;
            this.startsAt = startsAt;
            this.actionToken = actionToken;
        }
    }
}
