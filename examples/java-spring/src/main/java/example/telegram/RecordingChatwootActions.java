package example.telegram;

import java.util.ArrayList;
import java.util.List;

/** Offline recorder; it never stores reply text or calls Chatwoot. */
public final class RecordingChatwootActions implements ChatwootSupportHandoff.ChatwootActions {
    private final List<String> actions = new ArrayList<>();

    @Override
    public synchronized void assignConversation(long conversationId, String agentId) {
        actions.add("assign conversation " + conversationId + " to " + agentId);
    }

    @Override
    public synchronized void sendOperatorReply(long conversationId, String text) {
        actions.add("request reply for conversation " + conversationId);
    }

    public synchronized List<String> actions() {
        return List.copyOf(actions);
    }
}
