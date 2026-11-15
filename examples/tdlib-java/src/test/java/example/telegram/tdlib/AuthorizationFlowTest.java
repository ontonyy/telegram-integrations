package example.telegram.tdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class AuthorizationFlowTest {
    @Test
    void onlyAllowsTheActionForTheCurrentAuthorizationState() throws Exception {
        FakeTdLibPort port = new FakeTdLibPort();
        AuthorizationFlow flow = new AuthorizationFlow(port);

        flow.setParameters(configuration());
        assertEquals(List.of("parameters"), port.actions);

        flow.onState(AuthState.WAIT_PHONE_NUMBER);
        flow.submitPhoneNumber("+10000000000");
        flow.onState(AuthState.WAIT_CODE);
        flow.submitCode("00000");
        flow.onState(AuthState.WAIT_PASSWORD);
        flow.submitPassword("test-password");
        flow.onState(AuthState.READY);
        assertEquals(List.of("Sample chat"), flow.listVisibleChats());
        flow.logOut();

        assertEquals(List.of("parameters", "phone", "code", "password", "list", "logout"), port.actions);
        flow.onState(AuthState.LOGGING_OUT);
        assertThrows(IllegalStateException.class, flow::logOut);
    }

    @Test
    void neverAllowsAuthenticationInputsBeforeTheMatchingState() {
        FakeTdLibPort port = new FakeTdLibPort();
        AuthorizationFlow flow = new AuthorizationFlow(port);

        assertThrows(IllegalStateException.class, () -> flow.submitPhoneNumber("+10000000000"));
        assertThrows(IllegalStateException.class, () -> flow.submitCode("00000"));
        assertThrows(IllegalStateException.class, flow::listVisibleChats);
        assertEquals(List.of(), port.actions);
    }

    private static TdLibConfiguration configuration() {
        return new TdLibConfiguration(1, "test-hash", Path.of("/tmp/tdlib-test"), new byte[32]);
    }

    private static final class FakeTdLibPort implements TdLibPort {
        private final List<String> actions = new ArrayList<>();

        @Override public void start(Consumer<AuthState> stateUpdates) { }
        @Override public void setParameters(TdLibConfiguration configuration) { actions.add("parameters"); }
        @Override public void setPhoneNumber(String phoneNumber) { actions.add("phone"); }
        @Override public void checkCode(String code) { actions.add("code"); }
        @Override public void checkPassword(String password) { actions.add("password"); }
        @Override public List<String> listVisibleMainChatTitles() { actions.add("list"); return List.of("Sample chat"); }
        @Override public void logOut() { actions.add("logout"); }
        @Override public void close() { actions.add("close"); }
    }
}
