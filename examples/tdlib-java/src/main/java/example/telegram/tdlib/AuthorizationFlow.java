package example.telegram.tdlib;

import java.util.Objects;
import java.util.List;

/** Enforces the small, explicit set of actions exposed by the demo. */
public final class AuthorizationFlow {
    private final TdLibPort port;
    private AuthState state = AuthState.WAIT_TDLIB_PARAMETERS;

    public AuthorizationFlow(TdLibPort port) {
        this.port = Objects.requireNonNull(port);
    }

    public synchronized AuthState state() {
        return state;
    }

    public synchronized void onState(AuthState next) {
        state = Objects.requireNonNull(next);
    }

    public synchronized void setParameters(TdLibConfiguration configuration) {
        require(AuthState.WAIT_TDLIB_PARAMETERS);
        port.setParameters(configuration);
    }

    public synchronized void submitPhoneNumber(String phoneNumber) {
        require(AuthState.WAIT_PHONE_NUMBER);
        port.setPhoneNumber(nonBlank(phoneNumber, "Phone number is required"));
    }

    public synchronized void submitCode(String code) {
        require(AuthState.WAIT_CODE);
        port.checkCode(nonBlank(code, "Login code is required"));
    }

    public synchronized void submitPassword(String password) {
        require(AuthState.WAIT_PASSWORD);
        port.checkPassword(nonBlank(password, "Two-step verification password is required"));
    }

    public synchronized List<String> listVisibleChats() throws Exception {
        require(AuthState.READY);
        return port.listVisibleMainChatTitles();
    }

    public synchronized void logOut() {
        require(AuthState.READY);
        port.logOut();
    }

    public synchronized void close() {
        if (state != AuthState.CLOSED) {
            port.close();
        }
    }

    private void require(AuthState expected) {
        if (state != expected) {
            throw new IllegalStateException("Action is not allowed in authorization state " + state);
        }
    }

    private static String nonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
