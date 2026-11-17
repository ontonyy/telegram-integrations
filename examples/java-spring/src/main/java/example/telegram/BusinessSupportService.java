package example.telegram;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** Mock-backed Business support state. Customer message bodies are deliberately never retained. */
@Service
public class BusinessSupportService {
    private static final Duration BUSINESS_REPLY_WINDOW = Duration.ofHours(24);
    private final BusinessMessageSender sender;
    private final BusinessOperatorAuthorizer operatorAuthorizer;
    private final Clock clock;
    private final AtomicLong ticketSequence = new AtomicLong();
    private final Map<String, Connection> connections = new LinkedHashMap<>();
    private final Map<String, MutableTicket> tickets = new LinkedHashMap<>();
    private final Map<String, String> ticketRefsByConversation = new LinkedHashMap<>();

    @Autowired
    public BusinessSupportService(
            BusinessMessageSender sender, BusinessOperatorAuthorizer operatorAuthorizer) {
        this(sender, operatorAuthorizer, Clock.systemUTC());
    }

    BusinessSupportService(
            BusinessMessageSender sender, BusinessOperatorAuthorizer operatorAuthorizer, Clock clock) {
        this.sender = sender;
        this.operatorAuthorizer = operatorAuthorizer;
        this.clock = clock;
    }

    public synchronized void handleUpdate(JsonNode update) {
        JsonNode connectionUpdate = update.path("business_connection");
        if (connectionUpdate.isObject()) {
            updateConnection(connectionUpdate);
        }

        JsonNode newMessage = update.path("business_message");
        if (newMessage.isObject()) {
            handleMessage(newMessage, MessageChange.NEW);
        }
        JsonNode editedMessage = update.path("edited_business_message");
        if (editedMessage.isObject()) {
            handleMessage(editedMessage, MessageChange.EDITED);
        }
        JsonNode deletedMessages = update.path("deleted_business_messages");
        if (deletedMessages.isObject()) {
            handleDeletion(deletedMessages);
        }
    }

    public synchronized List<Ticket> tickets() {
        return tickets.values().stream().map(MutableTicket::snapshot).toList();
    }

    public synchronized TakeoverResult takeOver(String ticketRef, String operatorId) {
        if (!operatorAuthorizer.isAuthorized(operatorId)) {
            return new TakeoverResult(false, "Operator is not authorized.");
        }
        MutableTicket ticket = tickets.get(ticketRef);
        if (ticket == null) {
            return new TakeoverResult(false, "Ticket was not found.");
        }
        ticket.status = TicketStatus.HUMAN_ASSIGNED;
        ticket.assignedTo = operatorId;
        return new TakeoverResult(true, "Conversation assigned to the authorized operator.");
    }

    public synchronized ReplyResult replyAsOperator(String ticketRef, String operatorId, String text) {
        MutableTicket ticket = tickets.get(ticketRef);
        if (ticket == null) {
            return ReplyResult.fallback("Ticket was not found.");
        }
        if (!operatorAuthorizer.isAuthorized(operatorId)
                || ticket.status != TicketStatus.HUMAN_ASSIGNED
                || !Objects.equals(ticket.assignedTo, operatorId)) {
            return ReplyResult.fallback("An authorized assigned operator must take over this ticket first.");
        }
        if (text == null || text.isBlank() || text.length() > 4_000) {
            return ReplyResult.fallback("Reply text must contain 1-4000 characters.");
        }
        Connection connection = connections.get(ticket.connectionId);
        if (connection == null || !connection.enabled) {
            return ReplyResult.fallback("Business connection is unavailable; reply manually from Telegram.");
        }
        if (!connection.canReply) {
            return ReplyResult.fallback("Business connection lacks can_reply; reply manually from Telegram.");
        }
        Instant now = clock.instant();
        if (ticket.lastIncomingAt == null
                || now.isBefore(ticket.lastIncomingAt)
                || !now.isBefore(ticket.lastIncomingAt.plus(BUSINESS_REPLY_WINDOW))) {
            return ReplyResult.fallback("Telegram's 24-hour reply window is unavailable; reply manually from Telegram.");
        }

        sender.sendBusinessMessageOrLog(ticket.connectionId, ticket.chatId, text.strip());
        return new ReplyResult(true, true, "Reply submitted through the business connection.");
    }

    private void updateConnection(JsonNode payload) {
        String connectionId = payload.path("id").asText("");
        if (connectionId.isBlank()) {
            return;
        }
        JsonNode userId = payload.path("user").path("id");
        JsonNode rights = payload.path("rights");
        connections.put(connectionId, new Connection(
                userId.isIntegralNumber() ? userId.asText() : "",
                payload.path("is_enabled").asBoolean(false),
                rights.path("can_reply").asBoolean(false)));
    }

    private void handleMessage(JsonNode message, MessageChange change) {
        String connectionId = message.path("business_connection_id").asText("");
        String chatId = idAsString(message.path("chat").path("id"));
        String chatType = message.path("chat").path("type").asText("");
        String senderId = idAsString(message.path("from").path("id"));
        long messageId = message.path("message_id").asLong(-1);
        long date = message.path("date").asLong(-1);
        if (connectionId.isBlank() || chatId.isBlank() || messageId < 0 || date < 0
                || !"private".equals(chatType)) {
            return;
        }
        Connection connection = connections.get(connectionId);
        if (connection == null || connection.ownerUserId.isBlank() || senderId.isBlank()
                || connection.ownerUserId.equals(senderId)) {
            return;
        }

        String conversationKey = conversationKey(connectionId, chatId);
        String ticketRef = ticketRefsByConversation.computeIfAbsent(conversationKey,
                ignored -> "BUS-" + ticketSequence.incrementAndGet());
        MutableTicket ticket = tickets.computeIfAbsent(ticketRef,
                ignored -> new MutableTicket(ticketRef, connectionId, chatId));
        Instant incomingAt = Instant.ofEpochSecond(date);
        if (change == MessageChange.NEW
                && (ticket.lastIncomingAt == null
                        || incomingAt.isAfter(ticket.lastIncomingAt)
                        || (incomingAt.equals(ticket.lastIncomingAt)
                                && messageId > ticket.lastCustomerMessageId))) {
            ticket.lastCustomerMessageId = messageId;
            ticket.lastIncomingAt = incomingAt;
        }
        ticket.lastChange = change;
        ticket.deletedMessageIds.clear();
        if (ticket.status == TicketStatus.CLOSED) {
            ticket.status = TicketStatus.OPEN;
            ticket.assignedTo = "support-queue";
        }
    }

    private void handleDeletion(JsonNode deleted) {
        String connectionId = deleted.path("business_connection_id").asText("");
        String chatId = idAsString(deleted.path("chat").path("id"));
        if (connectionId.isBlank() || chatId.isBlank()) {
            return;
        }
        String ticketRef = ticketRefsByConversation.get(conversationKey(connectionId, chatId));
        MutableTicket ticket = tickets.get(ticketRef);
        if (ticket == null || !deleted.path("message_ids").isArray()) {
            return;
        }
        for (JsonNode messageId : deleted.path("message_ids")) {
            if (messageId.canConvertToLong()) {
                ticket.deletedMessageIds.add(messageId.asLong());
            }
        }
        ticket.lastChange = MessageChange.DELETED;
    }

    private String idAsString(JsonNode node) {
        return node.isIntegralNumber() ? node.asText() : "";
    }

    private String conversationKey(String connectionId, String chatId) {
        return connectionId + ":" + chatId;
    }

    public enum TicketStatus { OPEN, HUMAN_ASSIGNED, CLOSED }

    public enum MessageChange { NEW, EDITED, DELETED }

    public record Ticket(
            String reference,
            String conversationReference,
            String status,
            String assignedTo,
            Long lastCustomerMessageId,
            Instant lastIncomingAt,
            String lastChange,
            List<Long> deletedMessageIds) {}

    public record TakeoverResult(boolean assigned, String message) {}

    public record ReplyResult(boolean accepted, boolean submittedThroughBusinessConnection, String message) {
        private static ReplyResult fallback(String message) {
            return new ReplyResult(false, false, message);
        }
    }

    private record Connection(String ownerUserId, boolean enabled, boolean canReply) {}

    private static final class MutableTicket {
        private final String reference;
        private final String connectionId;
        private final String chatId;
        private final List<Long> deletedMessageIds = new ArrayList<>();
        private TicketStatus status = TicketStatus.OPEN;
        private String assignedTo = "support-queue";
        private Long lastCustomerMessageId;
        private Instant lastIncomingAt;
        private MessageChange lastChange = MessageChange.NEW;

        private MutableTicket(String reference, String connectionId, String chatId) {
            this.reference = reference;
            this.connectionId = connectionId;
            this.chatId = chatId;
        }

        private Ticket snapshot() {
            return new Ticket(
                    reference,
                    "Telegram conversation " + chatId + " (message " + lastCustomerMessageId + ")",
                    status.name(),
                    assignedTo,
                    lastCustomerMessageId,
                    lastIncomingAt,
                    lastChange.name(),
                    List.copyOf(deletedMessageIds));
        }
    }
}
