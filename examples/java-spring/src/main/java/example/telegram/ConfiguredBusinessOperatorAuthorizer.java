package example.telegram;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredBusinessOperatorAuthorizer implements BusinessOperatorAuthorizer {
    private final Set<String> operatorIds;

    public ConfiguredBusinessOperatorAuthorizer(
            @Value("${telegram.business.operator-ids:}") String configuredOperatorIds) {
        this.operatorIds = Arrays.stream(configuredOperatorIds.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean isAuthorized(String operatorId) {
        return operatorId != null && operatorIds.contains(operatorId);
    }
}
