package example.telegram;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InMemoryTelegramUpdateRegistry implements TelegramUpdateRegistry {
    private static final int MAX_RECORDED_UPDATES = 10_000;
    private final Map<Long, Boolean> processed = new LinkedHashMap<>();

    @Override
    public synchronized boolean processOnce(long updateId, Runnable action) {
        if (processed.containsKey(updateId)) {
            return false;
        }

        action.run();
        processed.put(updateId, Boolean.TRUE);
        if (processed.size() > MAX_RECORDED_UPDATES) {
            Iterator<Long> oldest = processed.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }
}
