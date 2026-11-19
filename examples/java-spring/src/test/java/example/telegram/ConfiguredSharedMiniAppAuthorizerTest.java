package example.telegram;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfiguredSharedMiniAppAuthorizerTest {
    @Test
    void requiresAnExactConfiguredChatAndUserPair() {
        ConfiguredSharedMiniAppAuthorizer authorizer =
                new ConfiguredSharedMiniAppAuthorizer("chat-a=101, chat-b=202");

        assertTrue(authorizer.mayAccess("chat-a", "101"));
        assertTrue(authorizer.mayAccess("chat-b", "202"));
        assertFalse(authorizer.mayAccess("chat-a", "202"));
        assertFalse(authorizer.mayAccess("chat-c", "101"));
    }

    @Test
    void deniesEverythingWhenConfigurationIsEmptyAndRejectsMalformedEntries() {
        ConfiguredSharedMiniAppAuthorizer authorizer = new ConfiguredSharedMiniAppAuthorizer("");
        assertFalse(authorizer.mayAccess("chat-a", "101"));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfiguredSharedMiniAppAuthorizer("chat-a"));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfiguredSharedMiniAppAuthorizer("chat-a= "));
    }
}
