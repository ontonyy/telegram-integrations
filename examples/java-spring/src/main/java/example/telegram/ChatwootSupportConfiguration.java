package example.telegram;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires only the local recording fake; this sample has no Chatwoot HTTP client. */
@Configuration
public class ChatwootSupportConfiguration {
    @Bean
    ChatwootSupportHandoff.ChatwootActions chatwootActions() {
        return new RecordingChatwootActions();
    }

    @Bean
    ChatwootSupportHandoff chatwootSupportHandoff(
            ChatwootSupportHandoff.ChatwootActions actions,
            @Value("${chatwoot.allowed-agent-ids:}") String configuredAgentIds) {
        Set<String> allowedAgents = Arrays.stream(configuredAgentIds.split(","))
                .map(String::strip)
                .filter(agent -> !agent.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return new ChatwootSupportHandoff(allowedAgents, actions);
    }
}
