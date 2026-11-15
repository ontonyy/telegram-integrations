package example.telegram;

/** Idempotency seam; production deployments should replace the local implementation with durable shared storage. */
public interface TelegramUpdateRegistry {
    boolean processOnce(long updateId, Runnable action);
}
