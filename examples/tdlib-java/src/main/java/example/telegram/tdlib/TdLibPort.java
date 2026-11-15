package example.telegram.tdlib;

import java.util.List;
import java.util.function.Consumer;

/** Narrow seam around TDLib so the authorization flow can be tested without JNI or Telegram. */
public interface TdLibPort extends AutoCloseable {
    void start(Consumer<AuthState> stateUpdates);

    void setParameters(TdLibConfiguration configuration);

    void setPhoneNumber(String phoneNumber);

    void checkCode(String code);

    void checkPassword(String password);

    List<String> listVisibleMainChatTitles() throws Exception;

    void logOut();

    @Override
    void close();
}
