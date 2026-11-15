package example.telegram;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Demo identity mapping supplied by server-side configuration. Use an enterprise identity provider in production. */
@Component
public class ConfiguredInternalRoleDirectory implements InternalRoleDirectory {
    private final Map<String, Set<Role>> rolesByTelegramId;

    public ConfiguredInternalRoleDirectory(
            @Value("${internal.employee-telegram-ids:}") String employeeIds,
            @Value("${internal.manager-telegram-ids:}") String managerIds) {
        Map<String, Set<Role>> configured = new HashMap<>();
        parseIds(employeeIds).forEach(id -> configured.put(id, Set.of(Role.EMPLOYEE)));
        parseIds(managerIds).forEach(id -> configured.merge(id, Set.of(Role.MANAGER), (existing, ignored) ->
                Set.of(Role.EMPLOYEE, Role.MANAGER)));
        rolesByTelegramId = Map.copyOf(configured);
    }

    @Override
    public Set<Role> rolesFor(String telegramUserId) {
        return rolesByTelegramId.getOrDefault(telegramUserId, Set.of());
    }

    private Set<String> parseIds(String configuredIds) {
        if (configuredIds == null || configuredIds.isBlank()) {
            return Set.of();
        }
        Set<String> ids = Arrays.stream(configuredIds.split(","))
                .map(String::strip)
                .filter(value -> value.matches("[0-9]{1,20}"))
                .collect(Collectors.toUnmodifiableSet());
        return ids;
    }
}
