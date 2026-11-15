package example.telegram;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.file.Path;

@FunctionalInterface
public interface FileDownloadTransport {
    void download(HttpRequest request, Path destination, long maxBytes) throws IOException, InterruptedException;
}
