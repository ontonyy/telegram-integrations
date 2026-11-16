package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class TelegramWebhookBodyLimitFilterTest {
    private static final int MAX_BODY_BYTES = 10;

    private final RecordingEndpoint endpoint = new RecordingEndpoint();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint)
                .addFilters(new TelegramWebhookBodyLimitFilter(MAX_BODY_BYTES))
                .build();
    }

    @Test
    void rejectsBodyLargerThanLimitBeforeCallingTheEndpoint() throws Exception {
        mockMvc.perform(post("/telegram/webhook").content("01234567890"))
                .andExpect(status().isContentTooLarge());

        assertEquals(0, endpoint.calls.get());
    }

    @Test
    void acceptsBodyExactlyAtLimit() throws Exception {
        mockMvc.perform(post("/telegram/webhook").content("0123456789"))
                .andExpect(status().isOk());

        assertEquals(1, endpoint.calls.get());
    }

    @Test
    void rejectsOversizedBodyWhenContentLengthIsUnavailable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/telegram/webhook");
        request.setContent("01234567890".getBytes(StandardCharsets.UTF_8));
        HttpServletRequest withoutContentLength = new HttpServletRequestWrapper(request) {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TelegramWebhookBodyLimitFilter(MAX_BODY_BYTES).doFilter(
                withoutContentLength, response, (servletRequest, servletResponse) -> endpoint.calls.incrementAndGet());

        assertEquals(HttpStatus.CONTENT_TOO_LARGE.value(), response.getStatus());
        assertEquals(0, endpoint.calls.get());
    }

    @RestController
    private static final class RecordingEndpoint {
        private final AtomicInteger calls = new AtomicInteger();

        @PostMapping("/telegram/webhook")
        ResponseEntity<Void> receive(@RequestBody String body) {
            calls.incrementAndGet();
            return ResponseEntity.ok().build();
        }
    }
}
