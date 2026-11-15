package example.telegram;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Bounded per-process retry de-duplication; use durable shared storage for production. */
@Component
public class JiraWebhookRegistry {
    private static final int MAX_IDENTIFIERS = 10_000;
    private final Set<String> processed = new LinkedHashSet<>();

    public synchronized boolean processOnce(String identifier, Runnable action) {
        if (processed.contains(identifier)) {
            return false;
        }
        action.run();
        processed.add(identifier);
        if (processed.size() > MAX_IDENTIFIERS) {
            Iterator<String> oldest = processed.iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }
}
