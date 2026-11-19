package example.telegram;

@FunctionalInterface
public interface SharedMiniAppAuthorizer {
    boolean mayAccess(String chatInstance, String userId);
}
