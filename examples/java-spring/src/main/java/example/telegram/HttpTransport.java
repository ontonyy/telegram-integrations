package example.telegram;

import java.io.IOException;
import java.net.http.HttpRequest;

/** A small seam that lets provider clients be tested without opening a network connection. */
@FunctionalInterface
public interface HttpTransport {
    TransportResponse send(HttpRequest request) throws IOException, InterruptedException;
}
