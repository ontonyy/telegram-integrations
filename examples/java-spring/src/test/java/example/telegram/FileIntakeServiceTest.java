package example.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileIntakeServiceTest {
    private static final long MAX_BYTES = 256;
    @TempDir
    Path tempDir;
    private FakeTelegramFileGateway gateway;

    @BeforeEach
    void createGateway() {
        gateway = new FakeTelegramFileGateway();
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void rejectsMissingOrOversizeMetadataBeforeCallingTelegram() {
        FileIntakeService service = service();

        FileIntakeService.Result unknownSize = service.process(document("trace.log", "text/plain", -1));
        FileIntakeService.Result oversize = service.process(document("trace.log", "text/plain", MAX_BYTES + 1));

        assertFalse(unknownSize.accepted());
        assertFalse(oversize.accepted());
        assertEquals(0, gateway.getFileCalls);
    }

    @Test
    void rejectsUnsupportedExtensionMimeAndTraversalNamesBeforeDownload() {
        FileIntakeService service = service();

        assertFalse(service.process(document("archive.zip", "application/zip", 20)).accepted());
        assertFalse(service.process(document("../trace.log", "text/plain", 20)).accepted());
        assertFalse(service.process(document("trace.log", "application/pdf", 20)).accepted());
        assertFalse(service.process(document("C:\\private\\trace.log", "text/plain", 20)).accepted());
        assertEquals(0, gateway.getFileCalls);
    }

    @Test
    void downloadsAndProcessesPlainTextThenDeletesTemporaryFileAndDirectory() throws Exception {
        gateway.content = ("java.lang.IllegalStateException: synthetic\n\tat sample.Service.run(Service.java:1)\n")
                .getBytes(StandardCharsets.UTF_8);
        FileIntakeService.Result result = service().process(document("trace.log", "text/plain", gateway.content.length));

        assertTrue(result.accepted());
        assertTrue(result.reply().contains("stack-trace-like content detected"));
        assertTrue(result.reply().contains("deleted"));
        assertFalse(result.reply().contains("IllegalStateException"));
        assertEquals(1, gateway.getFileCalls);
        assertEquals(1, gateway.downloadCalls);
        try (var remaining = Files.list(tempDir)) {
            assertEquals(0, remaining.count());
        }
    }

    @Test
    void rejectsFileLargerThanLimitAfterDownloadAndStillCleansUp() throws Exception {
        gateway.content = "x".repeat((int) MAX_BYTES + 1).getBytes(StandardCharsets.UTF_8);

        FileIntakeService.Result result = service().process(document("trace.txt", "text/plain", 20));

        assertFalse(result.accepted());
        try (var remaining = Files.list(tempDir)) {
            assertEquals(0, remaining.count());
        }
    }

    @Test
    void rejectsOversizeMetadataReturnedByGetFileBeforeOpeningTemporaryStorage() throws Exception {
        gateway.reportedSize = MAX_BYTES + 1;

        FileIntakeService.Result result = service().process(document("trace.log", "text/plain", 20));

        assertFalse(result.accepted());
        assertEquals(0, gateway.downloadCalls);
        try (var remaining = Files.list(tempDir)) {
            assertEquals(0, remaining.count());
        }
    }

    @Test
    void failedDownloadCleansUpTemporaryFileAndDirectory() throws Exception {
        gateway.failDownload = true;

        FileIntakeService.Result result = service().process(document("sample.java", "text/x-java-source", 20));

        assertFalse(result.accepted());
        assertTrue(result.reply().contains("discarded"));
        try (var remaining = Files.list(tempDir)) {
            assertEquals(0, remaining.count());
        }
    }

    @Test
    void cancellationRestoresInterruptAndDeletesTemporaryData() throws Exception {
        gateway.interruptDownload = true;

        FileIntakeService.Result result = service().process(document("sample.java", "text/plain", 20));

        assertFalse(result.accepted());
        assertTrue(Thread.currentThread().isInterrupted());
        try (var remaining = Files.list(tempDir)) {
            assertEquals(0, remaining.count());
        }
    }

    private FileIntakeService service() {
        return new FileIntakeService(gateway, MAX_BYTES, tempDir);
    }

    private FileIntakeService.DocumentMetadata document(String name, String mime, long size) {
        return new FileIntakeService.DocumentMetadata("synthetic-file-id", name, mime, size);
    }

    private static final class FakeTelegramFileGateway implements TelegramFileGateway {
        private byte[] content = "synthetic text".getBytes(StandardCharsets.UTF_8);
        private int getFileCalls;
        private int downloadCalls;
        private boolean failDownload;
        private boolean interruptDownload;
        private long reportedSize = -1;

        @Override
        public DownloadInfo getFile(String fileId) {
            getFileCalls++;
            return new DownloadInfo("documents/synthetic.txt", reportedSize < 0 ? content.length : reportedSize);
        }

        @Override
        public void download(String filePath, Path destination, long maxBytes)
                throws IOException, InterruptedException {
            downloadCalls++;
            if (interruptDownload) {
                throw new InterruptedException("synthetic cancellation");
            }
            if (failDownload) {
                Files.write(destination, "partial".getBytes(StandardCharsets.UTF_8));
                throw new IOException("synthetic download failure");
            }
            Files.write(destination, content);
        }
    }
}
