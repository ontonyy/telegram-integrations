package example.telegram;

import java.util.List;
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
public class SharedMiniAppController {
    private final String botToken;
    private final TelegramInitDataValidator validator;
    private final SharedMiniAppAuthorizer authorizer;
    private final SharedAvailabilityBoardService boards;
    private final ObjectMapper mapper;

    public SharedMiniAppController(
            @Value("${telegram.bot-token:}") String botToken,
            TelegramInitDataValidator validator,
            SharedMiniAppAuthorizer authorizer,
            SharedAvailabilityBoardService boards,
            ObjectMapper mapper) {
        this.botToken = botToken;
        this.validator = validator;
        this.authorizer = authorizer;
        this.boards = boards;
        this.mapper = mapper;
    }

    @PostMapping("/api/miniapp/shared/board")
    public ResponseEntity<?> getBoard(@RequestBody SharedBoardRequest body) {
        VerifiedLaunch launch = verifiedGroupLaunch(body.initData());
        if (launch == null) {
            return unauthorized();
        }
        if (!authorizer.mayAccess(launch.chatInstance(), launch.userId())) {
            return forbidden();
        }
        return ResponseEntity.ok(boards.snapshot(launch.chatInstance(), launch.userId()));
    }

    @PostMapping("/api/miniapp/shared/availability")
    public ResponseEntity<?> updateAvailability(@RequestBody UpdateAvailabilityRequest body) {
        VerifiedLaunch launch = verifiedGroupLaunch(body.initData());
        if (launch == null) {
            return unauthorized();
        }
        if (!authorizer.mayAccess(launch.chatInstance(), launch.userId())) {
            return forbidden();
        }
        if (body.expectedVersion() == null || body.slots() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "expectedVersion and slots are required."));
        }

        SharedAvailabilityBoardService.UpdateResult result;
        try {
            result = boards.update(launch.chatInstance(), launch.userId(), body.expectedVersion(), body.slots());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
        if (result.status() == SharedAvailabilityBoardService.UpdateStatus.CONFLICT) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Board changed since it was read. Review the current board and retry.",
                    "board", result.snapshot()));
        }
        if (result.status() == SharedAvailabilityBoardService.UpdateStatus.CAPACITY_REACHED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "This demo board has reached its participant limit."));
        }
        return ResponseEntity.ok(result.snapshot());
    }

    private VerifiedLaunch verifiedGroupLaunch(String initData) {
        try {
            Map<String, String> data = validator.validate(initData, botToken);
            JsonNode user = mapper.readTree(data.getOrDefault("user", "{}"));
            JsonNode id = user.path("id");
            String userId = id.isIntegralNumber() && id.canConvertToLong() && id.asLong() > 0
                    ? id.asText()
                    : null;
            String chatInstance = data.get("chat_instance");
            String chatType = data.get("chat_type");
            if (userId == null || chatInstance == null || chatInstance.isBlank()
                    || !("group".equals(chatType) || "supergroup".equals(chatType))) {
                return null;
            }
            return new VerifiedLaunch(userId, chatInstance, chatType);
        } catch (IllegalArgumentException | JacksonException exception) {
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "verified", false,
                "error", "A fresh signed group Mini App launch is required."));
    }

    private ResponseEntity<Map<String, Object>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "This signed user and chat context are not enabled for the shared demo."));
    }

    public record SharedBoardRequest(String initData) {}

    public record UpdateAvailabilityRequest(String initData, Long expectedVersion, List<String> slots) {}

    private record VerifiedLaunch(String userId, String chatInstance, String chatType) {}
}
