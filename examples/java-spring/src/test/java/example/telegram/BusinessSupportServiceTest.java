package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class BusinessSupportServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();
    private final RecordingBusinessSender sender = new RecordingBusinessSender();
    private BusinessSupportService service;

    @BeforeEach
    void setUp() {
        service = new BusinessSupportService(
                sender, "operator-7"::equals, Clock.fixed(NOW, ZoneOffset.UTC));
        service.handleUpdate(json("""
                {"business_connection":{"id":"business-connection-demo","user":{"id":900},
                 "is_enabled":true,"rights":{"can_reply":true}}}
                """));
    }

    @Test
    void createsMinimalTicketForPrivateBusinessMessageAndTracksEditsAndDeletion() {
        service.handleUpdate(customerUpdate("business_message", 301, NOW.minusSeconds(60)));
        BusinessSupportService.Ticket ticket = service.tickets().getFirst();

        assertEquals("OPEN", ticket.status());
        assertEquals("support-queue", ticket.assignedTo());
        assertEquals(301L, ticket.lastCustomerMessageId());
        assertFalse(ticket.toString().contains("synthetic customer text"));

        service.handleUpdate(customerUpdate("edited_business_message", 301, NOW.minusSeconds(30)));
        assertEquals("EDITED", service.tickets().getFirst().lastChange());

        service.handleUpdate(json("""
                {"deleted_business_messages":{"business_connection_id":"business-connection-demo",
                 "chat":{"id":555,"type":"private"},"message_ids":[301]}}
                """));
        BusinessSupportService.Ticket deleted = service.tickets().getFirst();
        assertEquals("DELETED", deleted.lastChange());
        assertEquals(List.of(301L), deleted.deletedMessageIds());
    }

    @Test
    void ignoresGroupMessagesAndMessagesSentByTheBusinessAccountOwner() {
        service.handleUpdate(json("""
                {"business_message":{"business_connection_id":"business-connection-demo",
                 "message_id":302,"date":1794743940,"chat":{"id":-555,"type":"supergroup"},
                 "from":{"id":44},"text":"synthetic customer text"}}
                """));
        service.handleUpdate(json("""
                {"business_message":{"business_connection_id":"business-connection-demo",
                 "message_id":303,"date":1794743940,"chat":{"id":555,"type":"private"},
                 "from":{"id":900},"text":"synthetic owner message"}}
                """));

        assertTrue(service.tickets().isEmpty());
    }

    @Test
    void authorizedOperatorTakesOverAndReplyUsesBusinessConnection() {
        service.handleUpdate(customerUpdate("business_message", 304, NOW.minusSeconds(1)));
        String ticketRef = service.tickets().getFirst().reference();

        assertFalse(service.takeOver(ticketRef, "operator-9").assigned());
        assertTrue(service.takeOver(ticketRef, "operator-7").assigned());
        BusinessSupportService.ReplyResult result =
                service.replyAsOperator(ticketRef, "operator-7", "A reviewed reply");

        assertTrue(result.accepted());
        assertTrue(result.submittedThroughBusinessConnection());
        assertEquals("business-connection-demo", sender.connectionId);
        assertEquals("555", sender.chatId);
        assertEquals("A reviewed reply", sender.text);
    }

    @Test
    void fallsBackWithoutSendingWhenConnectionRightOrReplyWindowDoesNotPermitReply() {
        service.handleUpdate(customerUpdate("business_message", 305, NOW.minusSeconds(10)));
        String ticketRef = service.tickets().getFirst().reference();
        service.takeOver(ticketRef, "operator-7");

        service.handleUpdate(json("""
                {"business_connection":{"id":"business-connection-demo","user":{"id":900},
                 "is_enabled":true,"rights":{"can_reply":false}}}
                """));
        assertFalse(service.replyAsOperator(ticketRef, "operator-7", "No right").accepted());

        service.handleUpdate(json("""
                {"business_connection":{"id":"business-connection-demo","user":{"id":900},
                 "is_enabled":false,"rights":{"can_reply":true}}}
                """));
        assertFalse(service.replyAsOperator(ticketRef, "operator-7", "Disconnected").accepted());

        service.handleUpdate(json("""
                {"business_connection":{"id":"business-connection-demo","user":{"id":900},
                 "is_enabled":true,"rights":{"can_reply":true}}}
                """));
        service.handleUpdate(customerUpdate("business_message", 306, NOW.minusSeconds(86_401)));
        assertFalse(service.replyAsOperator(ticketRef, "operator-7", "Too late").accepted());
        assertEquals(0, sender.sendCount);
    }

    private JsonNode customerUpdate(String updateType, long messageId, Instant sentAt) {
        return json("""
                {"%s":{"business_connection_id":"business-connection-demo","message_id":%d,
                 "date":%d,"chat":{"id":555,"type":"private"},"from":{"id":42},
                 "text":"synthetic customer text"}}
                """.formatted(updateType, messageId, sentAt.getEpochSecond()));
    }

    private JsonNode json(String input) {
        try {
            return mapper.readTree(input);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    private static final class RecordingBusinessSender implements BusinessMessageSender {
        private int sendCount;
        private String connectionId;
        private String chatId;
        private String text;

        @Override
        public void sendBusinessMessageOrLog(String connectionId, String chatId, String text) {
            sendCount++;
            this.connectionId = connectionId;
            this.chatId = chatId;
            this.text = text;
        }
    }
}
