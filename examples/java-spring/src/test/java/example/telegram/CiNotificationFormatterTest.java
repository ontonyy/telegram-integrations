package example.telegram;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CiNotificationFormatterTest {
    @Test
    void formatsFailureWithEscapedHtmlAndShortCommit() {
        String message = CiNotificationFormatter.format(
                "failure", "owner/<project>&team'\"", "feature/<bad>", "123456789abcdef", "https://github.com/o/r/actions/runs/10");

        assertTrue(message.contains("<b>CI Failure</b>"));
        assertTrue(message.contains("<code>owner/&lt;project&gt;&amp;team&#39;&quot;</code>"));
        assertTrue(message.contains("feature/&lt;bad&gt;"));
        assertTrue(message.contains("12345678"));
        assertFalse(message.contains("123456789abcdef"));
        assertTrue(message.endsWith("https://github.com/o/r/actions/runs/10"));
    }

    @Test
    void mapsSuccessAndCancelledStatuses() {
        assertTrue(CiNotificationFormatter.format("success", "o/r", "main", "12345678", "https://example.test/run")
                .contains("CI Success"));
        assertTrue(CiNotificationFormatter.format("cancelled", "o/r", "main", "12345678", "https://example.test/run")
                .contains("CI Cancelled"));
    }
}
