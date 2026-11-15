# Optional TDLib Java account-client example

**Status:** code and local fake-port tests only. No TDLib account, native library, API credentials, phone number, login code or session was used in this repository change.

This standalone example uses Telegram's official TDLib Java/JNI API in an optional source set. It demonstrates the explicit authorization flow and one read-only local action: list up to ten titles from the signed-in account's main chat list. It does not fetch message history, send messages, index content, or call an AI provider. The fake-port tests run without TDLib, JNI, network access or secrets.

TDLib is a full Telegram client library. The account session and encrypted database are sensitive account-level credentials. A user's technical access to a chat does not itself authorize collection, indexing, AI processing or disclosure to another service. Review the [Telegram API Terms](https://core.telegram.org/api/terms) and [Content Licensing Terms](https://telegram.org/tos/content-licensing) for the intended use.

## Local checks (fake port only)

From the repository root, run the separate tests with the Java sample's Gradle wrapper:

```bash
./examples/java-spring/gradlew -p examples/tdlib-java test
```

These tests exercise only the authorization-state guard and a synthetic fake port. They do not load JNI, connect to Telegram, or establish that a real login works.

## Native prerequisites

The official Java API is generated Java code backed by a native TDLib JNI library; it is not a normal pure-Java dependency. You need a JDK, CMake, a C++ toolchain and the platform dependencies listed in [TDLib's build instructions](https://tdlib.github.io/td/build.html?language=Java). The official example documents Windows MSVC architecture selection and vcpkg, and requires the Java classes and native library to target the same architecture as the JVM. Linux and macOS builds also need their documented native dependencies. This repository has not built or tested the native adapter on any operating system.

Follow TDLib's [official Java example build steps](https://github.com/tdlib/td/blob/master/example/java/README.md) in a separate checkout. The documented CMake flow is:

```bash
git clone https://github.com/tdlib/td.git /tmp/tdlib
cd /tmp/tdlib
mkdir jnibuild
cd jnibuild
cmake -DCMAKE_BUILD_TYPE=Release -DTD_ENABLE_JNI=ON -DCMAKE_INSTALL_PREFIX:PATH=../example/java/td ..
cmake --build . --target install
cd ../example/java
mkdir build
cd build
cmake -DCMAKE_BUILD_TYPE=Release -DTd_DIR=/tmp/tdlib/example/java/td/lib/cmake/Td -DCMAKE_INSTALL_PREFIX:PATH=.. ..
cmake --build . --target install
```

After the official build, point the optional task at the generated classes and native library directory (the official README places the Java example output under `example/java/bin`):

```bash
TDLIB_JAVA_CLASSPATH=/absolute/path/to/td/example/java/bin \
TDLIB_NATIVE_LIBRARY_PATH=/absolute/path/to/td/example/java/bin \
./examples/java-spring/gradlew -p examples/tdlib-java compileTdlibAdapter
```

The source code and fake tests are separate from the optional JNI adapter; compiling the adapter verifies Java types against the locally built official TDLib classes. `compileTdlibAdapter` does not start the client or authorize an account. The exact `bin` output may vary by platform and TDLib build; point both variables to the actual generated classes and `tdjni` library locations.

## Optional manual account test

This step creates a real Telegram client session. It is deliberately separate from tests and requires your own Telegram application credentials and an account you control. Do not run it as part of CI.

1. Obtain an API ID and API hash for your own application from [my.telegram.org](https://my.telegram.org). Do not use credentials copied from tutorials or another application.
2. Set `TDLIB_API_ID` and `TDLIB_API_HASH` in the process environment or a secret manager. Set `TDLIB_DATA_DIR` to an absolute private directory **outside this repository**. The default is `~/.local/share/telegram-tdlib-java-demo`.
3. The demo asks for a 32-byte Base64 database encryption key through a hidden terminal prompt, or reads it from `TDLIB_DATABASE_KEY_B64`. Keep the key in a password manager/secret manager; without it the local database cannot be reopened.
4. Run only from an interactive terminal after you have built the official TDLib Java/native example:

```bash
TDLIB_API_ID='<your app id>' \
TDLIB_API_HASH='<your app hash>' \
TDLIB_DATA_DIR="$HOME/.local/share/telegram-tdlib-java-demo" \
TDLIB_JAVA_CLASSPATH=/absolute/path/to/td/example/java/bin \
TDLIB_NATIVE_LIBRARY_PATH=/absolute/path/to/td/example/java/bin \
./examples/java-spring/gradlew -p examples/tdlib-java runTdlibDemo
```

The phone number, login code and two-step verification password are read without echo and are not written by this example. The TDLib database key and API credentials are held only for the process lifetime by the demo; TDLib persists its encrypted account database under `TDLIB_DATA_DIR`. The demo turns off message/chat-info/file databases and secret-chat support, and only fetches main-list chat titles after the user types `list`.

Type `logout` to log out the local TDLib client. Logging out locally is distinct from reviewing/revoking sessions in Telegram: use Telegram **Settings → Devices** to terminate the session if needed. Type `quit` to close TDLib while preserving its encrypted local database for a later login. After the client has closed, delete the local data directory and remove the stored database key to erase the local session. For example on Linux/macOS:

```bash
rm -rf "$HOME/.local/share/telegram-tdlib-java-demo"
```

If you set a custom `TDLIB_DATA_DIR`, delete that exact directory instead. On Windows, remove the custom directory with File Explorer or `Remove-Item -Recurse -Force` after the process exits, then remove its key from the secret manager. Directory permissions are restricted to the current user where POSIX permissions are available; use equivalent Windows ACLs otherwise.

## Scope and limitations

- Authorization states handled by the demo: TDLib parameter setup, phone number, login code, two-step password, ready, logging out, closing and closed. Other states stop the demo rather than guessing at an action.
- The visible-chat action is a local display of at most ten main-list chat titles returned by TDLib for the signed-in account. It does not export them or read message bodies.
- This is a desktop Java/JNI example. Android/iOS packaging and platform lifecycle are outside its scope. The official native build instructions determine which OS/JDK/compiler combinations are usable.
- No automated test authorizes a real account. No native build, live Telegram login, chat read, logout or remote session revocation was run or verified.

## Sources checked 2026-11-15

- [Official TDLib Java API](https://core.telegram.org/tdlib/Java_API)
- [Official TDLib Java example and native build steps](https://github.com/tdlib/td/blob/master/example/java/README.md)
- [Official TDLib Java `Client` implementation](https://github.com/tdlib/td/blob/master/example/java/org/drinkless/tdlib/Client.java)
- [TDLib overview](https://core.telegram.org/tdlib/)
- [Telegram API Terms](https://core.telegram.org/api/terms)
- [Telegram Content Licensing Terms](https://telegram.org/tos/content-licensing)
