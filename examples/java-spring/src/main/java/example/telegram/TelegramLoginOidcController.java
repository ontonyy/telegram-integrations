package example.telegram;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Returns only the stable OIDC subject; it does not derive application roles from Telegram claims. */
@RestController
@RequestMapping("/login-demo")
public class TelegramLoginOidcController {
    @GetMapping("/me")
    public ResponseEntity<IdentityResponse> currentIdentity(@AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new IdentityResponse(user.getSubject()));
    }

    public record IdentityResponse(String subject) {}
}
