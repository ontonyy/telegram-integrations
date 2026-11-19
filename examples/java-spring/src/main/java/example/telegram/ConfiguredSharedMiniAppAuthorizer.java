package example.telegram;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Exact chat/user membership pairs; an empty configuration denies all access. */
@Component
public class ConfiguredSharedMiniAppAuthorizer implements SharedMiniAppAuthorizer {
    private final Set<Membership> memberships;

    public ConfiguredSharedMiniAppAuthorizer(
            @Value("${telegram.miniapp.shared.allowed-memberships:}") String configuredMemberships) {
        this.memberships = Arrays.stream(configuredMemberships.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(ConfiguredSharedMiniAppAuthorizer::parseMembership)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean mayAccess(String chatInstance, String userId) {
        return memberships.contains(new Membership(chatInstance, userId));
    }

    private static Membership parseMembership(String value) {
        int separator = value.indexOf('=');
        if (separator <= 0 || separator == value.length() - 1
                || value.indexOf('=', separator + 1) >= 0) {
            throw new IllegalArgumentException(
                    "Shared Mini App memberships must use chat_instance=user_id entries");
        }
        String chatInstance = value.substring(0, separator).strip();
        String userId = value.substring(separator + 1).strip();
        if (chatInstance.isEmpty() || userId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Shared Mini App memberships must use non-empty chat_instance=user_id entries");
        }
        return new Membership(chatInstance, userId);
    }

    private record Membership(String chatInstance, String userId) {}
}
