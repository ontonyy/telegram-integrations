package example.telegram;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Verifies and routes one Chatwoot event while keeping message content out of the handoff seam. */
@Component
public class ChatwootWebhookProcessor {
    private final ChatwootWebhookSignatureValidator signatures;
    private final ChatwootWebhookEventRegistry events;
    private final ChatwootSupportHandoff handoff;
    private final ObjectMapper mapper;

    public ChatwootWebhookProcessor(
            ChatwootWebhookSignatureValidator signatures,
            ChatwootWebhookEventRegistry events,
            ChatwootSupportHandoff handoff,
            ObjectMapper mapper) {
        this.signatures = signatures;
        this.events = events;
        this.handoff = handoff;
        this.mapper = mapper;
    }

    public Outcome process(byte[] rawBody, String timestamp, String signature, String secret) {
        if (!signatures.isValid(rawBody, timestamp, signature, secret)) {
            return Outcome.INVALID_SIGNATURE;
        }
        try {
            JsonNode payload = mapper.readTree(rawBody);
            if (!payload.isObject()) {
                return Outcome.INVALID_PAYLOAD;
            }
            if (!"message_created".equals(payload.path("event").asText())
                    || !"incoming".equals(payload.path("message_type").asText())
                    || !"contact".equals(payload.path("sender").path("type").asText())
                    || (payload.hasNonNull("content_type")
                            && !"text".equals(payload.path("content_type").asText()))) {
                return Outcome.IGNORED;
            }

            String messageId = positiveId(payload.path("id"));
            String accountId = positiveId(payload.path("account").path("id"));
            String conversationId = positiveId(payload.path("conversation").path("id"));
            String contactId = positiveId(payload.path("sender").path("id"));
            if (messageId == null || accountId == null || conversationId == null || contactId == null) {
                return Outcome.INVALID_PAYLOAD;
            }

            long conversation = Long.parseLong(conversationId);
            String eventKey = accountId + ":message:" + messageId;
            boolean processed = events.processOnce(eventKey, () -> {
                if (!handoff.acceptCustomerMessage(conversation, contactId)) {
                    throw new IllegalArgumentException("Chatwoot customer event was not accepted");
                }
            });
            return processed ? Outcome.ACCEPTED : Outcome.DUPLICATE;
        } catch (JacksonException | NumberFormatException exception) {
            return Outcome.INVALID_PAYLOAD;
        } catch (RuntimeException exception) {
            return Outcome.RETRYABLE_FAILURE;
        }
    }

    private String positiveId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        if (!value.matches("[1-9][0-9]{0,18}")) {
            return null;
        }
        try {
            Long.parseLong(value);
            return value;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public enum Outcome {
        ACCEPTED,
        DUPLICATE,
        IGNORED,
        INVALID_SIGNATURE,
        INVALID_PAYLOAD,
        RETRYABLE_FAILURE
    }
}
