package example.telegram;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class JdkHttpTransport implements HttpTransport {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public TransportResponse send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new TransportResponse(response.statusCode(), response.body());
    }
}
