package example.telegram.tdlib;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

/** Optional adapter against the Java classes generated and built by official TDLib. */
public final class OfficialTdLibPort implements TdLibPort {
    private final BlockingQueue<AuthState> states = new LinkedBlockingQueue<>();
    private final AtomicInteger pendingChatTitles = new AtomicInteger();
    private volatile Consumer<AuthState> stateUpdates = ignored -> { };
    private volatile Client client;

    @Override
    public void start(Consumer<AuthState> stateUpdates) {
        this.stateUpdates = stateUpdates;
        try {
            Client.execute(new TdApi.SetLogVerbosityLevel(0));
        } catch (Client.ExecutionException exception) {
            throw new IllegalStateException("Could not configure TDLib logging", exception);
        }
        client = Client.create(this::receive, null, exception ->
                System.err.println("TDLib request failed. Inspect local diagnostics without sharing them."));
    }

    private void receive(TdApi.Object object) {
        if (object instanceof TdApi.UpdateAuthorizationState update) {
            AuthState state = mapState(update.authorizationState);
            states.offer(state);
            stateUpdates.accept(state);
        }
    }

    @Override
    public void setParameters(TdLibConfiguration configuration) {
        TdApi.SetTdlibParameters parameters = new TdApi.SetTdlibParameters();
        parameters.databaseDirectory = configuration.databaseDirectory().toString();
        parameters.filesDirectory = configuration.databaseDirectory().resolve("files").toString();
        parameters.databaseEncryptionKey = configuration.databaseEncryptionKey();
        parameters.useFileDatabase = false;
        parameters.useChatInfoDatabase = false;
        parameters.useMessageDatabase = false;
        parameters.useSecretChats = false;
        parameters.apiId = configuration.apiId();
        parameters.apiHash = configuration.apiHash();
        parameters.systemLanguageCode = "en";
        parameters.deviceModel = "Java TDLib lifecycle demo";
        parameters.applicationVersion = "0.1.0";
        client.send(parameters, this::reportRequestError);
    }

    @Override
    public void setPhoneNumber(String phoneNumber) {
        client.send(new TdApi.SetAuthenticationPhoneNumber(phoneNumber, null), this::reportRequestError);
    }

    @Override
    public void checkCode(String code) {
        client.send(new TdApi.CheckAuthenticationCode(code), this::reportRequestError);
    }

    @Override
    public void checkPassword(String password) {
        client.send(new TdApi.CheckAuthenticationPassword(password), this::reportRequestError);
    }

    @Override
    public List<String> listVisibleMainChatTitles() throws Exception {
        CompletableFuture<List<String>> result = new CompletableFuture<>();
        List<String> titles = java.util.Collections.synchronizedList(new ArrayList<>());
        client.send(new TdApi.GetChats(new TdApi.ChatListMain(), 10), object -> {
            if (object instanceof TdApi.Error) {
                result.completeExceptionally(new IllegalStateException("TDLib could not list chats"));
                return;
            }
            if (!(object instanceof TdApi.Chats chats)) {
                result.completeExceptionally(new IllegalStateException("TDLib returned an unexpected chat-list response"));
                return;
            }
            if (chats.chatIds.length == 0) {
                result.complete(List.of());
                return;
            }
            pendingChatTitles.set(chats.chatIds.length);
            for (long chatId : chats.chatIds) {
                client.send(new TdApi.GetChat(chatId), chat -> {
                    if (chat instanceof TdApi.Chat visibleChat) {
                        titles.add(visibleChat.title);
                    }
                    if (pendingChatTitles.decrementAndGet() == 0) {
                        result.complete(List.copyOf(titles));
                    }
                });
            }
        });
        return result.get(30, TimeUnit.SECONDS);
    }

    @Override
    public void logOut() {
        client.send(new TdApi.LogOut(), this::reportRequestError);
    }

    @Override
    public void close() {
        Client currentClient = client;
        if (currentClient == null) {
            return;
        }
        currentClient.send(new TdApi.Close(), this::reportRequestError);
        try {
            AuthState state;
            do {
                state = states.poll(10, TimeUnit.SECONDS);
            } while (state != null && state != AuthState.CLOSED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void reportRequestError(TdApi.Object object) {
        if (object instanceof TdApi.Error) {
            System.err.println("TDLib rejected a request. The response is not printed to avoid exposing account data.");
        }
    }

    private static AuthState mapState(TdApi.AuthorizationState state) {
        if (state instanceof TdApi.AuthorizationStateWaitTdlibParameters) return AuthState.WAIT_TDLIB_PARAMETERS;
        if (state instanceof TdApi.AuthorizationStateWaitPhoneNumber) return AuthState.WAIT_PHONE_NUMBER;
        if (state instanceof TdApi.AuthorizationStateWaitCode) return AuthState.WAIT_CODE;
        if (state instanceof TdApi.AuthorizationStateWaitPassword) return AuthState.WAIT_PASSWORD;
        if (state instanceof TdApi.AuthorizationStateReady) return AuthState.READY;
        if (state instanceof TdApi.AuthorizationStateLoggingOut) return AuthState.LOGGING_OUT;
        if (state instanceof TdApi.AuthorizationStateClosing) return AuthState.CLOSING;
        if (state instanceof TdApi.AuthorizationStateClosed) return AuthState.CLOSED;
        return AuthState.UNSUPPORTED;
    }

    public AuthState awaitState() throws InterruptedException {
        return states.take();
    }
}
