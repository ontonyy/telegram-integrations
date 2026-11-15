package example.telegram;

import java.io.IOException;
import java.nio.file.Path;

/** Adapter for the Bot API getFile then file download flow. */
public interface TelegramFileGateway {
    DownloadInfo getFile(String fileId) throws IOException, InterruptedException;

    void download(String filePath, Path destination, long maxBytes) throws IOException, InterruptedException;

    record DownloadInfo(String filePath, long fileSize) {}
}
