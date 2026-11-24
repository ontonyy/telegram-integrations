package example.telegram;

/** Idempotency seam for signed Chatwoot message events. */
public interface ChatwootWebhookEventRegistry {
    boolean processOnce(String eventKey, Runnable action);
}
