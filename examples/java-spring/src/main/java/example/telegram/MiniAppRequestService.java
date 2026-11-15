package example.telegram;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** In-memory ownership-scoped example; replace with durable application storage before deployment. */
@Service
public class MiniAppRequestService {
    private static final int MAX_REQUESTS = 1_000;
    private final Map<String, StoredRequest> requests = new LinkedHashMap<>();

    public synchronized RequestView create(String ownerId, String title, String details) {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        RequestView view = new RequestView(requestId, title, details, "RECEIVED", Instant.now().toString());
        requests.put(requestId, new StoredRequest(ownerId, view));
        if (requests.size() > MAX_REQUESTS) {
            requests.remove(requests.keySet().iterator().next());
        }
        return view;
    }

    public synchronized Optional<RequestView> findVisibleTo(String requestId, String ownerId) {
        StoredRequest request = requests.get(requestId);
        if (request == null || !request.ownerId().equals(ownerId)) {
            return Optional.empty();
        }
        return Optional.of(request.view());
    }

    private record StoredRequest(String ownerId, RequestView view) {}

    public record RequestView(
            String requestId, String title, String details, String status, String createdAt) {}
}
