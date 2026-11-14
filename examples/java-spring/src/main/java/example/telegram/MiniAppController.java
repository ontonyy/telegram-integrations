package example.telegram;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MiniAppController {
    private final String botToken;
    private final TelegramInitDataValidator validator;

    public MiniAppController(
            @Value("${telegram.bot-token:}") String botToken,
            TelegramInitDataValidator validator) {
        this.botToken = botToken;
        this.validator = validator;
    }

    @PostMapping("/api/miniapp/session")
    public ResponseEntity<?> createSession(@RequestBody Map<String, String> body) {
        try {
            Map<String, String> verified = validator.validate(body.get("initData"), botToken);
            return ResponseEntity.ok(Map.of(
                    "verified", true,
                    "telegramUser", verified.getOrDefault("user", "{}"),
                    "note", "Apply application-specific authorization before returning customer data."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("verified", false, "error", exception.getMessage()));
        }
    }
}
