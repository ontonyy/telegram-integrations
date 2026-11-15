package example.telegram.tdlib;

import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

/** A deliberately small, user-driven client lifecycle demonstration. */
public final class OfficialTdLibDemo {
    private OfficialTdLibDemo() { }

    public static void main(String[] args) throws Exception {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Run this demo from a terminal so login inputs can be hidden.");
        }

        int apiId = Integer.parseInt(requiredEnvironment("TDLIB_API_ID"));
        String apiHash = requiredEnvironment("TDLIB_API_HASH");
        Path databaseDirectory = databaseDirectory();
        byte[] databaseKey = databaseKey(console);

        OfficialTdLibPort port = new OfficialTdLibPort();
        AuthorizationFlow flow = new AuthorizationFlow(port);
        port.start(flow::onState);
        System.out.println("TDLib started. Authentication input is requested only for the current state.");

        try {
            while (flow.state() != AuthState.CLOSED) {
                AuthState state = port.awaitState();
                flow.onState(state);
                switch (state) {
                    case WAIT_TDLIB_PARAMETERS -> flow.setParameters(
                            new TdLibConfiguration(apiId, apiHash, databaseDirectory, databaseKey));
                    case WAIT_PHONE_NUMBER -> flow.submitPhoneNumber(readHidden(console, "Telegram phone number: "));
                    case WAIT_CODE -> flow.submitCode(readHidden(console, "Telegram login code: "));
                    case WAIT_PASSWORD -> flow.submitPassword(readHidden(console, "Two-step verification password: "));
                    case READY -> runAuthorizedMenu(console, flow);
                    case LOGGING_OUT -> System.out.println("Logging out this TDLib session…");
                    case CLOSING -> System.out.println("Closing TDLib…");
                    case CLOSED -> System.out.println("TDLib client closed.");
                    case UNSUPPORTED -> {
                        System.err.println("This TDLib authorization state is not supported by the demo; closing safely.");
                        flow.close();
                    }
                }
            }
        } finally {
            flow.close();
            java.util.Arrays.fill(databaseKey, (byte) 0);
        }
    }

    private static void runAuthorizedMenu(Console console, AuthorizationFlow flow) throws Exception {
        while (flow.state() == AuthState.READY) {
            String command = console.readLine("Authorized. Type 'list', 'logout', or 'quit': ");
            if (command == null || command.equalsIgnoreCase("quit")) {
                flow.close();
                return;
            }
            if (command.equalsIgnoreCase("logout")) {
                flow.logOut();
                return;
            }
            if (command.equalsIgnoreCase("list")) {
                List<String> titles = flow.listVisibleChats();
                if (titles.isEmpty()) {
                    System.out.println("No main-list chats were returned by TDLib.");
                } else {
                    titles.forEach(title -> System.out.println("- " + title));
                }
                continue;
            }
            System.out.println("Unknown command. No action was taken.");
        }
    }

    private static String readHidden(Console console, String prompt) {
        char[] value = console.readPassword("%s", prompt);
        if (value == null) throw new IllegalStateException("Terminal input ended");
        try {
            return new String(value);
        } finally {
            java.util.Arrays.fill(value, '\0');
        }
    }

    private static byte[] databaseKey(Console console) {
        String encoded = System.getenv("TDLIB_DATABASE_KEY_B64");
        if (encoded == null || encoded.isBlank()) {
            encoded = readHidden(console, "Base64 32-byte database encryption key (will not echo): ");
        }
        byte[] key = Base64.getDecoder().decode(encoded);
        if (key.length != 32) {
            java.util.Arrays.fill(key, (byte) 0);
            throw new IllegalArgumentException("TDLIB_DATABASE_KEY_B64 must decode to exactly 32 bytes");
        }
        return key;
    }

    private static Path databaseDirectory() throws Exception {
        Path directory = Path.of(System.getenv().getOrDefault(
                "TDLIB_DATA_DIR",
                Path.of(System.getProperty("user.home"), ".local", "share", "telegram-tdlib-java-demo").toString()))
                .toAbsolutePath().normalize();
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        if (directory.startsWith(workingDirectory)) {
            throw new IllegalArgumentException("TDLIB_DATA_DIR must be outside the current project directory");
        }
        Files.createDirectories(directory);
        try {
            Files.setPosixFilePermissions(directory, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            System.err.println("POSIX directory permissions are unavailable; secure this directory with OS ACLs.");
        }
        return directory;
    }

    private static String requiredEnvironment(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + key + " in the process environment; do not put it in source control.");
        }
        return value;
    }
}
