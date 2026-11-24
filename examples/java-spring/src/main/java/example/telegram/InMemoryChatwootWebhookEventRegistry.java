package example.telegram;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Bounded, process-local example registry; it is not a durable delivery guarantee. */
@Component
public class InMemoryChatwootWebhookEventRegistry implements ChatwootWebhookEventRegistry {
    private static final int MAX_RECORDED_EVENTS = 10_000;
    private final Map<String, Boolean> processed = new LinkedHashMap<>();

    @Override
    public synchronized boolean processOnce(String eventKey, Runnable action) {
        if (eventKey == null || eventKey.isBlank() || processed.containsKey(eventKey)) {
            return false;
        }
        action.run();
        processed.put(eventKey, Boolean.TRUE);
        if (processed.size() > MAX_RECORDED_EVENTS) {
            Iterator<String> oldest = processed.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }
}
