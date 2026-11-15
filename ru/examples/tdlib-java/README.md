# Необязательный пример TDLib Java для пользовательского аккаунта

**Статус:** только код и локальные тесты с fake port. При этом изменении репозитория TDLib-аккаунт не подключался; нативная библиотека, API-учётные данные, номер телефона, код входа и сессия не использовались.

Это отдельный пример с официальным Java/JNI API TDLib в необязательном source set. Он показывает явные этапы авторизации и одно локальное действие только для чтения: вывести до десяти названий чатов из основного списка вошедшего аккаунта. Он не загружает историю сообщений, не отправляет сообщения, не индексирует содержимое и не вызывает AI. Fake-port тесты работают без TDLib, JNI, сети и секретов.

TDLib — полноценная клиентская библиотека Telegram. Сессия аккаунта и зашифрованная база являются чувствительными учётными данными уровня аккаунта. Технический доступ пользователя к чату сам по себе не разрешает сбор, индексацию, обработку AI или передачу содержимого другой системе. Для конкретного сценария проверьте [Условия Telegram API](https://core.telegram.org/api/terms) и [Условия лицензирования контента](https://telegram.org/tos/content-licensing).

## Локальные проверки (только fake port)

Запустите тесты отдельно от корня репозитория, используя Gradle wrapper Java-примера:

```bash
./examples/java-spring/gradlew -p examples/tdlib-java test
```

Тесты проверяют только ограничение переходов авторизации и синтетический fake port. Они не загружают JNI, не подключаются к Telegram и не подтверждают работу настоящего входа.

## Нативные требования

Официальный Java API — это сгенерированный Java-код и нативная библиотека TDLib с JNI, а не обычная чисто Java-зависимость. Нужны JDK, CMake, C++ toolchain и зависимости платформы из [инструкции сборки TDLib](https://tdlib.github.io/td/build.html?language=Java). Официальный пример для Windows описывает выбор архитектуры MSVC и vcpkg; Java-классы и нативная библиотека должны совпадать по архитектуре с JVM. Для Linux и macOS также нужны зависимости из соответствующих инструкций. Этот репозиторий не собирался и не проверялся с нативным адаптером ни на одной ОС.

Следуйте [официальным шагам сборки Java-примера TDLib](https://github.com/tdlib/td/blob/master/example/java/README.md) в отдельном checkout. Документированная последовательность CMake:

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

После официальной сборки укажите optional task путь к сгенерированным классам и папке нативной библиотеки (официальный README указывает выход Java-примера в `example/java/bin`):

```bash
TDLIB_JAVA_CLASSPATH=/absolute/path/to/td/example/java/bin \
TDLIB_NATIVE_LIBRARY_PATH=/absolute/path/to/td/example/java/bin \
./examples/java-spring/gradlew -p examples/tdlib-java compileTdlibAdapter
```

Исходный код и fake-тесты отделены от необязательного JNI-адаптера; его компиляция проверит Java-типы по локально собранным официальным классам TDLib. Задача `compileTdlibAdapter` не запускает клиент и не авторизует аккаунт. Точные выходные пути `bin` могут отличаться по платформе и настройкам TDLib: укажите каталоги с реальными классами и библиотекой `tdjni`.

## Необязательная ручная проверка с аккаунтом

Этот шаг создаёт реальную клиентскую сессию Telegram. Он отделён от тестов и требует собственные credentials приложения Telegram и аккаунт, которым вы управляете. Не запускайте его в CI.

1. Получите собственные API ID и API hash на [my.telegram.org](https://my.telegram.org). Не используйте данные из учебников или чужих приложений.
2. Установите `TDLIB_API_ID` и `TDLIB_API_HASH` в окружении процесса или secret manager. Установите `TDLIB_DATA_DIR` в абсолютную приватную папку **вне репозитория**. По умолчанию используется `~/.local/share/telegram-tdlib-java-demo`.
3. Пример спросит 32-байтовый ключ шифрования базы в формате Base64 через скрытый ввод терминала или прочитает его из `TDLIB_DATABASE_KEY_B64`. Храните ключ в password manager/secret manager: без него локальную базу нельзя открыть повторно.
4. Запускайте из интерактивного терминала после сборки официальных Java-классов и native library TDLib:

```bash
TDLIB_API_ID='<your app id>' \
TDLIB_API_HASH='<your app hash>' \
TDLIB_DATA_DIR="$HOME/.local/share/telegram-tdlib-java-demo" \
TDLIB_JAVA_CLASSPATH=/absolute/path/to/td/example/java/bin \
TDLIB_NATIVE_LIBRARY_PATH=/absolute/path/to/td/example/java/bin \
./examples/java-spring/gradlew -p examples/tdlib-java runTdlibDemo
```

Номер телефона, код входа и пароль двухэтапной проверки вводятся без отображения и этим примером не записываются. Ключ и API credentials используются только в памяти процесса; TDLib сохраняет зашифрованную базу аккаунта в `TDLIB_DATA_DIR`. Пример отключает базы сообщений, информации о чатах и файлах, а также поддержку секретных чатов. Он получает названия чатов основного списка только после явной команды `list`.

Команда `logout` завершает сессию этого локального TDLib-клиента. Это отличается от проверки и отзыва активных сессий в Telegram: при необходимости используйте Telegram **Настройки → Устройства**. Команда `quit` закрывает TDLib и оставляет зашифрованную локальную базу для следующего входа. После закрытия клиента удалите каталог данных и секретный ключ. Например, в Linux/macOS:

```bash
rm -rf "$HOME/.local/share/telegram-tdlib-java-demo"
```

Если задан собственный `TDLIB_DATA_DIR`, удалите именно этот каталог. В Windows удалите его через проводник или `Remove-Item -Recurse -Force` после завершения процесса, а затем удалите ключ из secret manager. Где доступны POSIX-права, доступ к каталогу ограничен текущим пользователем; в остальных случаях настройте эквивалентные Windows ACL.

## Область и ограничения

- Пример обрабатывает состояния авторизации TDLib: инициализация параметров, номер, код входа, пароль двухэтапной проверки, готовность, выход, закрытие и закрытое состояние. Для других состояний он завершает работу, а не угадывает допустимое действие.
- Команда просмотра локально показывает до десяти названий чатов основного списка, которые TDLib возвращает вошедшему аккаунту. Содержимое не экспортируется, тела сообщений не читаются.
- Это пример для desktop Java/JNI. Сборка Android/iOS и жизненный цикл мобильной платформы не входят в область. Поддерживаемые комбинации ОС/JDK/компилятора определяются официальной инструкцией нативной сборки.
- Автоматические тесты не авторизуют настоящий аккаунт. Нативная сборка, вход, чтение чатов, выход из аккаунта и удалённый отзыв сессии не выполнялись и не проверялись.

## Источники, проверенные 15 ноября 2026 года

- [Официальный Java API TDLib](https://core.telegram.org/tdlib/Java_API)
- [Официальный Java-пример TDLib и сборка native-компонентов](https://github.com/tdlib/td/blob/master/example/java/README.md)
- [Реализация официального Java `Client` TDLib](https://github.com/tdlib/td/blob/master/example/java/org/drinkless/tdlib/Client.java)
- [Обзор TDLib](https://core.telegram.org/tdlib/)
- [Условия Telegram API](https://core.telegram.org/api/terms)
- [Условия Telegram по лицензированию контента](https://telegram.org/tos/content-licensing)
