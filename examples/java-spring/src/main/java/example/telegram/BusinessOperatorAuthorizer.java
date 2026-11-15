package example.telegram;

public interface BusinessOperatorAuthorizer {
    boolean isAuthorized(String operatorId);
}
