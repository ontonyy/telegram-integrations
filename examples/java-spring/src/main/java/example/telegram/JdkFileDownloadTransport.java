package example.telegram;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class JdkFileDownloadTransport implements FileDownloadTransport {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public void download(HttpRequest request, Path destination, long maxBytes)
            throws IOException, InterruptedException {
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Telegram file endpoint returned HTTP " + response.statusCode());
            }
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > maxBytes) {
                throw new IOException("Telegram file exceeds the configured limit");
            }
            try (OutputStream output = Files.newOutputStream(
                    destination, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[8_192];
                long total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (total + read > maxBytes) {
                        throw new IOException("Telegram file exceeds the configured limit");
                    }
                    output.write(buffer, 0, read);
                    total += read;
                }
            }
        }
    }
}
