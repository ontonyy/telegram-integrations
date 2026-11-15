package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatwootSupportHandoffTest {
    private final RecordingActions actions = new RecordingActions();
    private final ChatwootSupportHandoff handoff = new ChatwootSupportHandoff(Set.of("agent-7"), actions);

    @Test
    void customerConversationCanBeAssignedAndRepliedToByTheAssignedOperator() {
        assertTrue(handoff.acceptCustomerMessage(481, "telegram-contact-demo", "Synthetic order question"));
        assertTrue(handoff.assign(481, "agent-7").accepted());
        assertTrue(handoff.reply(481, "agent-7", "Synthetic reply for order DEMO-42").accepted());

        assertEquals(List.of("assign:481:agent-7", "reply:481:Synthetic reply for order DEMO-42"), actions.calls);
        assertEquals(Set.of(481L), handoff.assignedConversations());
    }

    @Test
    void unknownOperatorUnassignedConversationAndInvalidReplyNeverReachChatwoot() {
        assertFalse(handoff.acceptCustomerMessage(481, "telegram-contact-demo", "  "));
        assertFalse(handoff.assign(481, "agent-9").accepted());
        assertFalse(handoff.reply(481, "agent-7", "Must not send").accepted());
        assertTrue(handoff.acceptCustomerMessage(481, "telegram-contact-demo", "Synthetic order question"));
        assertTrue(handoff.assign(481, "agent-7").accepted());
        assertFalse(handoff.reply(481, "agent-9", "Must not send").accepted());
        assertFalse(handoff.reply(481, "agent-7", "  ").accepted());
        assertEquals(1, actions.calls.size());
        assertEquals("assign:481:agent-7", actions.calls.getFirst());
    }

    private static final class RecordingActions implements ChatwootSupportHandoff.ChatwootActions {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void assignConversation(long conversationId, String agentId) {
            calls.add("assign:" + conversationId + ":" + agentId);
        }

        @Override
        public void sendOperatorReply(long conversationId, String text) {
            calls.add("reply:" + conversationId + ":" + text);
        }
    }
}
