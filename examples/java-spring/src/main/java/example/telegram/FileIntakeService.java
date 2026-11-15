package example.telegram;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FileIntakeService {
    private static final long TELEGRAM_CLOUD_DOWNLOAD_LIMIT = 20L * 1024 * 1024;
    private static final int MAX_CONCURRENT_DOWNLOADS = 2;
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private final TelegramFileGateway gateway;
    private final long maxBytes;
    private final Path privateTempRoot;
    private final Semaphore downloadsInFlight = new Semaphore(MAX_CONCURRENT_DOWNLOADS);

    public FileIntakeService(
            TelegramFileGateway gateway,
            @Value("${telegram.file-max-bytes:1048576}") long maxBytes,
            @Value("${telegram.file-temp-dir:${java.io.tmpdir}/telegram-private-uploads}") Path privateTempRoot) {
        if (maxBytes <= 0 || maxBytes > TELEGRAM_CLOUD_DOWNLOAD_LIMIT) {
            throw new IllegalArgumentException("File intake limit must be within Telegram's current cloud download limit");
        }
        this.gateway = gateway;
        this.maxBytes = maxBytes;
        this.privateTempRoot = privateTempRoot;
    }

    public Result process(DocumentMetadata document) {
        String extension = allowedExtension(document.fileName());
        if (extension == null || !mimeMatches(extension, document.mimeType())) {
            return Result.rejected("That file type is not accepted. Use a supported text file.");
        }
        if (document.fileSize() <= 0 || document.fileSize() > maxBytes) {
            return Result.rejected("That file is empty, too large, or has no trustworthy size metadata.");
        }
        if (document.fileId() == null || document.fileId().isBlank() || document.fileId().length() > 512) {
            return Result.rejected("The file metadata is invalid.");
        }
        if (!downloadsInFlight.tryAcquire()) {
            return Result.rejected("File intake is busy; try again shortly.");
        }

        Path privateDirectory = null;
        Path temporaryFile = null;
        try {
            TelegramFileGateway.DownloadInfo info = gateway.getFile(document.fileId());
            if (info.fileSize() <= 0 || info.fileSize() > maxBytes) {
                return Result.rejected("That file exceeds the configured download limit.");
            }
            preparePrivateRoot();
            privateDirectory = createPrivateDirectory();
            temporaryFile = createPrivateFile(privateDirectory, extension);
            gateway.download(info.filePath(), temporaryFile, maxBytes);

            long actualSize = Files.size(temporaryFile);
            if (actualSize <= 0 || actualSize > maxBytes) {
                return Result.rejected("The downloaded file did not pass the size check.");
            }
            byte[] bytes = Files.readAllBytes(temporaryFile);
            String text = decodeText(bytes);
            if (text.indexOf('\0') >= 0) {
                return Result.rejected("The file is not accepted as plain text.");
            }
            int lines = (int) text.lines().count();
            boolean stackTraceLike = text.contains("Exception") && text.contains("\n\tat ");
            return new Result(true, "File accepted as text: " + actualSize + " bytes, " + lines + " lines; "
                    + (stackTraceLike ? "stack-trace-like content detected." : "no stack-trace pattern detected.")
                    + " The temporary file was deleted; contents were not retained or sent to AI.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.rejected("File processing was interrupted; temporary data was discarded.");
        } catch (Exception exception) {
            return Result.rejected("The file could not be safely processed; temporary data was discarded.");
        } finally {
            deleteQuietly(temporaryFile);
            deleteQuietly(privateDirectory);
            downloadsInFlight.release();
        }
    }

    private String allowedExtension(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.length() > 128
                || fileName.contains("/") || fileName.contains("\\") || fileName.indexOf('\0') >= 0) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : new String[] {".txt", ".log", ".java", ".json", ".csv"}) {
            if (lower.endsWith(extension) && fileName.length() > extension.length()) {
                return extension;
            }
        }
        return null;
    }

    private boolean mimeMatches(String extension, String mimeType) {
        if (mimeType == null || mimeType.isBlank() || mimeType.length() > 100) {
            return false;
        }
        String mime = mimeType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return switch (extension) {
            case ".txt", ".log", ".csv" -> Set.of("text/plain", "text/csv", "application/csv").contains(mime);
            case ".java" -> Set.of("text/plain", "text/x-java", "text/x-java-source").contains(mime);
            case ".json" -> Set.of("application/json", "text/json", "text/plain").contains(mime);
            default -> false;
        };
    }

    private String decodeText(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private void preparePrivateRoot() throws IOException {
        if (Files.exists(privateTempRoot, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(privateTempRoot) || !Files.isDirectory(privateTempRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Private temporary root must be a real directory");
            }
            setPermissions(privateTempRoot, DIRECTORY_PERMISSIONS);
            return;
        }
        try {
            Files.createDirectory(privateTempRoot,
                    PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
        } catch (UnsupportedOperationException exception) {
            Files.createDirectory(privateTempRoot);
            setPermissions(privateTempRoot, DIRECTORY_PERMISSIONS);
        } catch (FileAlreadyExistsException exception) {
            if (Files.isSymbolicLink(privateTempRoot)
                    || !Files.isDirectory(privateTempRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Private temporary root must be a real directory");
            }
            setPermissions(privateTempRoot, DIRECTORY_PERMISSIONS);
        }
    }

    private Path createPrivateDirectory() throws IOException {
        try {
            return Files.createTempDirectory(privateTempRoot, "upload-",
                    PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
        } catch (UnsupportedOperationException exception) {
            Path directory = Files.createTempDirectory(privateTempRoot, "upload-");
            setPermissions(directory, DIRECTORY_PERMISSIONS);
            return directory;
        }
    }

    private Path createPrivateFile(Path directory, String extension) throws IOException {
        try {
            return Files.createTempFile(directory, "content-", extension,
                    PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
        } catch (UnsupportedOperationException exception) {
            Path file = Files.createTempFile(directory, "content-", extension);
            setPermissions(file, FILE_PERMISSIONS);
            return file;
        }
    }

    private void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException exception) {
            java.io.File file = path.toFile();
            boolean restricted = file.setReadable(false, false)
                    && file.setWritable(false, false)
                    && file.setExecutable(false, false)
                    && file.setReadable(permissions.contains(PosixFilePermission.OWNER_READ), true)
                    && file.setWritable(permissions.contains(PosixFilePermission.OWNER_WRITE), true)
                    && file.setExecutable(permissions.contains(PosixFilePermission.OWNER_EXECUTE), true);
            if (!restricted) {
                throw new IOException("Could not apply private permissions to temporary data");
            }
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            System.err.println("Telegram temporary file cleanup failed.");
        }
    }

    public record DocumentMetadata(String fileId, String fileName, String mimeType, long fileSize) {}

    public record Result(boolean accepted, String reply) {
        private static Result rejected(String reply) {
            return new Result(false, reply);
        }
    }
}
