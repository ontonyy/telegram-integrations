package example.telegram;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mock-friendly choreography for a Chatwoot conversation handoff.
 * Chatwoot owns Telegram ingestion, conversation history, assignment UI, and channel delivery.
 * This optional adapter models the app-side routing decision; it is not a Telegram bot client.
 */
public final class ChatwootSupportHandoff {
    private final Set<String> allowedAgents;
    private final ChatwootActions actions;
    private final Map<Long, String> assignments = new LinkedHashMap<>();
    private final Set<Long> customerConversations = new LinkedHashSet<>();

    public ChatwootSupportHandoff(Set<String> allowedAgents, ChatwootActions actions) {
        this.allowedAgents = Set.copyOf(allowedAgents);
        this.actions = actions;
    }

    /** Called only after a trusted Chatwoot webhook identifies an incoming customer message. */
    public synchronized boolean acceptCustomerMessage(long conversationId, String contactId, String content) {
        if (conversationId <= 0 || contactId == null || contactId.isBlank()
                || content == null || content.isBlank()) {
            return false;
        }
        // Chatwoot remains the message system of record; this seam keeps only the conversation reference.
        customerConversations.add(conversationId);
        return true;
    }

    /** Called only after a trusted Chatwoot webhook identifies a customer message. */
    public synchronized HandoffResult assign(long conversationId, String agentId) {
        if (!customerConversations.contains(conversationId) || !allowedAgents.contains(agentId)) {
            return new HandoffResult(false, "Conversation or operator is not authorized.");
        }
        actions.assignConversation(conversationId, agentId);
        assignments.put(conversationId, agentId);
        return new HandoffResult(true, "Conversation assigned in Chatwoot.");
    }

    /** Models an operator reply action; the caller must authenticate the operator separately. */
    public synchronized HandoffResult reply(long conversationId, String agentId, String text) {
        if (!allowedAgents.contains(agentId)
                || !agentId.equals(assignments.get(conversationId))) {
            return new HandoffResult(false, "Only the authorized assigned operator can reply.");
        }
        if (text == null || text.isBlank() || text.length() > 4_000) {
            return new HandoffResult(false, "Reply text must contain 1-4000 characters.");
        }
        actions.sendOperatorReply(conversationId, text.strip());
        return new HandoffResult(true, "Reply handed to Chatwoot for channel delivery.");
    }

    public synchronized Set<Long> assignedConversations() {
        return new LinkedHashSet<>(assignments.keySet());
    }

    public interface ChatwootActions {
        void assignConversation(long conversationId, String agentId);

        void sendOperatorReply(long conversationId, String text);
    }

    public record HandoffResult(boolean accepted, String message) {}
}
