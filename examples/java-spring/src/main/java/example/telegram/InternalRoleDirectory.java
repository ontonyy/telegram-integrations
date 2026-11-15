package example.telegram;

import java.util.Set;

/** Resolves company roles from application-controlled identity data, never from Telegram update fields. */
public interface InternalRoleDirectory {
    Set<Role> rolesFor(String telegramUserId);

    enum Role {
        EMPLOYEE,
        MANAGER
    }
}
