package example.telegram;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds the JSON body before Spring parses a Telegram webhook update. */
@Component
public final class TelegramWebhookBodyLimitFilter extends OncePerRequestFilter {
    private static final int PROVIDER_WEBHOOK_MAX_BODY_BYTES = 1_000_000;
    private final int maxBodyBytes;

    public TelegramWebhookBodyLimitFilter(
            @Value("${telegram.webhook.max-body-bytes:1048576}") int maxBodyBytes) {
        if (maxBodyBytes < 1 || maxBodyBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Webhook body limit must be between 1 and 2147483646 bytes");
        }
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = requestPath(request);
        return !"POST".equals(request.getMethod())
                || !("/telegram/webhook".equals(path)
                        || "/jira/webhook".equals(path)
                        || "/grafana/webhook".equals(path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            jakarta.servlet.FilterChain filterChain) throws IOException, jakarta.servlet.ServletException {
        int requestLimit = requestLimit(request);
        if (request.getContentLengthLong() > requestLimit) {
            reject(response);
            return;
        }

        byte[] body = request.getInputStream().readNBytes(requestLimit + 1);
        if (body.length > requestLimit) {
            reject(response);
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void reject(HttpServletResponse response) {
        response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
    }

    private int requestLimit(HttpServletRequest request) {
        return "/telegram/webhook".equals(requestPath(request))
                ? maxBodyBytes
                : PROVIDER_WEBHOOK_MAX_BODY_BYTES;
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    try {
                        if (input.available() > 0) {
                            readListener.onDataAvailable();
                        }
                        if (input.available() == 0) {
                            readListener.onAllDataRead();
                        }
                    } catch (IOException exception) {
                        readListener.onError(exception);
                    }
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.ISO_8859_1 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
