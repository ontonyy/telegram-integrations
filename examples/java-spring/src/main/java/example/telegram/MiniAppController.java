package example.telegram;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
public class MiniAppController {
    private final String botToken;
    private final TelegramInitDataValidator validator;
    private final MiniAppRequestService requests;
    private final ObjectMapper mapper;

    public MiniAppController(
            @Value("${telegram.bot-token:}") String botToken,
            TelegramInitDataValidator validator,
            MiniAppRequestService requests,
            ObjectMapper mapper) {
        this.botToken = botToken;
        this.validator = validator;
        this.requests = requests;
        this.mapper = mapper;
    }

    @PostMapping("/api/miniapp/session")
    public ResponseEntity<?> createSession(@RequestBody MiniAppSessionRequest body) {
        try {
            String userId = verifiedUserId(body.initData());
            return ResponseEntity.ok(Map.of(
                    "verified", true,
                    "telegramUserId", userId,
                    "note", "Only this verified user can read their own synthetic requests."));
        } catch (IllegalArgumentException exception) {
            return unauthorized();
        }
    }

    @PostMapping("/api/miniapp/requests")
    public ResponseEntity<?> createRequest(@RequestBody CreateRequest body) {
        String ownerId;
        try {
            ownerId = verifiedUserId(body.initData());
        } catch (IllegalArgumentException exception) {
            return unauthorized();
        }
        if (body.title() == null || body.title().isBlank() || body.title().length() > 120
                || body.details() == null || body.details().length() > 2_000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title or details are invalid."));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requests.create(ownerId, body.title().strip(), body.details().strip()));
    }

    @PostMapping("/api/miniapp/requests/status")
    public ResponseEntity<?> getRequest(
            @RequestBody RequestStatusRequest body) {
        String ownerId;
        try {
            ownerId = verifiedUserId(body.initData());
        } catch (IllegalArgumentException exception) {
            return unauthorized();
        }
        return requests.findVisibleTo(body.requestId(), ownerId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private String verifiedUserId(String initData) {
        try {
            Map<String, String> verified = validator.validate(initData, botToken);
            JsonNode user = mapper.readTree(verified.getOrDefault("user", "{}"));
            JsonNode id = user.path("id");
            if (!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() <= 0) {
                throw new IllegalArgumentException("Verified launch does not contain a valid user.");
            }
            return id.asText();
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Verified launch does not contain a valid user.");
        }
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "verified", false,
                "error", "Telegram launch data is invalid or expired."));
    }

    public record MiniAppSessionRequest(String initData) {}

    public record CreateRequest(String initData, String title, String details) {}

    public record RequestStatusRequest(String initData, String requestId) {}
}
