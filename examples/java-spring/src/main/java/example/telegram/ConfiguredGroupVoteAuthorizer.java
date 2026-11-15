package example.telegram;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredGroupVoteAuthorizer implements GroupVoteAuthorizer {
    private final Set<String> allowedUserIds;

    public ConfiguredGroupVoteAuthorizer(@Value("${telegram.allowed-voter-ids:}") String configuredIds) {
        this.allowedUserIds = Arrays.stream(configuredIds.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean mayVote(String chatId, String userId) {
        return allowedUserIds.contains(userId);
    }
}
