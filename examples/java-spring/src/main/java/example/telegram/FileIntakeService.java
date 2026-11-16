package example.telegram;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.nio.file.StandardOpenOption;
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
    private static final String MAINTENANCE_LOCK = ".maintenance.lock";
    private static final String UPLOAD_PREFIX = "upload-";
    private static final String LEASE_FILE = ".lease";
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

        UploadWorkspace workspace = null;
        try {
            TelegramFileGateway.DownloadInfo info = gateway.getFile(document.fileId());
            if (info.fileSize() <= 0 || info.fileSize() > maxBytes) {
                return Result.rejected("That file exceeds the configured download limit.");
            }
            preparePrivateRoot();
            workspace = createWorkspace(extension);
            gateway.download(info.filePath(), workspace.temporaryFile(), maxBytes);

            long actualSize = Files.size(workspace.temporaryFile());
            if (actualSize <= 0 || actualSize > maxBytes) {
                return Result.rejected("The downloaded file did not pass the size check.");
            }
            byte[] bytes = Files.readAllBytes(workspace.temporaryFile());
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
            closeWorkspaceQuietly(workspace);
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

    private UploadWorkspace createWorkspace(String extension) throws IOException, InterruptedException {
        try (FileChannel channel = FileChannel.open(privateTempRoot.resolve(MAINTENANCE_LOCK),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = acquireLock(channel)) {
            cleanupAbandonedUploads();
            Path directory = createPrivateDirectory();
            FileChannel leaseChannel = null;
            FileLock lease = null;
            try {
                Path leasePath = directory.resolve(LEASE_FILE);
                leaseChannel = FileChannel.open(leasePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                lease = acquireLock(leaseChannel);
                setPermissions(leasePath, FILE_PERMISSIONS);
                Path temporaryFile = createPrivateFile(directory, extension);
                return new UploadWorkspace(directory, temporaryFile, leaseChannel, lease);
            } catch (IOException | InterruptedException | RuntimeException exception) {
                releaseQuietly(lease, leaseChannel);
                deleteManagedDirectory(directory);
                throw exception;
            }
        }
    }

    private Path createPrivateDirectory() throws IOException {
        try {
            return Files.createTempDirectory(privateTempRoot, UPLOAD_PREFIX,
                    PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
        } catch (UnsupportedOperationException exception) {
            Path directory = Files.createTempDirectory(privateTempRoot, UPLOAD_PREFIX);
            setPermissions(directory, DIRECTORY_PERMISSIONS);
            return directory;
        }
    }

    private void cleanupAbandonedUploads() throws IOException, InterruptedException {
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(privateTempRoot, UPLOAD_PREFIX + "*")) {
            for (Path directory : directories) {
                if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                Path leasePath = directory.resolve(LEASE_FILE);
                if (Files.exists(leasePath, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.isSymbolicLink(leasePath) || !Files.isRegularFile(leasePath, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    try (FileChannel channel = FileChannel.open(leasePath, StandardOpenOption.WRITE)) {
                        FileLock lease;
                        try {
                            lease = channel.tryLock();
                        } catch (OverlappingFileLockException exception) {
                            continue;
                        }
                        if (lease == null) {
                            continue;
                        }
                        try (lease) {
                            try {
                                deleteManagedDirectory(directory);
                            } catch (IOException ignored) {
                                // Leave unexpected or inaccessible contents untouched.
                            }
                        }
                    } catch (IOException ignored) {
                        // A workspace that cannot be inspected is left for an operator.
                    }
                } else {
                    // Workspace creation is serialized by the maintenance lock, so a missing lease
                    // means the creating process stopped before it could publish an active lease.
                    try {
                        deleteManagedDirectory(directory);
                    } catch (IOException ignored) {
                        // Leave unexpected or inaccessible contents untouched.
                    }
                }
            }
        }
    }

    private void deleteManagedDirectory(Path directory) throws IOException {
        try (DirectoryStream<Path> contents = Files.newDirectoryStream(directory)) {
            for (Path entry : contents) {
                if (Files.isSymbolicLink(entry) || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Unexpected entry in private upload directory");
                }
                Files.deleteIfExists(entry);
            }
        }
        Files.deleteIfExists(directory);
    }

    private FileLock acquireLock(FileChannel channel) throws IOException, InterruptedException {
        while (true) {
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) {
                    return lock;
                }
            } catch (OverlappingFileLockException ignored) {
                // File locks held by another thread in this JVM are reported as overlapping.
            }
            Thread.sleep(10);
        }
    }

    private void closeWorkspaceQuietly(UploadWorkspace workspace) {
        if (workspace == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(privateTempRoot.resolve(MAINTENANCE_LOCK),
                StandardOpenOption.WRITE);
                FileLock ignored = acquireLock(channel)) {
            deleteQuietly(workspace.temporaryFile());
            releaseQuietly(workspace.lease(), workspace.leaseChannel());
            deleteQuietly(workspace.directory().resolve(LEASE_FILE));
            deleteQuietly(workspace.directory());
        } catch (Exception exception) {
            releaseQuietly(workspace.lease(), workspace.leaseChannel());
            System.err.println("Telegram temporary workspace cleanup failed.");
        }
    }

    private void releaseQuietly(FileLock lock, FileChannel channel) {
        try {
            if (lock != null) {
                lock.release();
            }
        } catch (IOException exception) {
            System.err.println("Telegram temporary upload lease release failed.");
        }
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException exception) {
            System.err.println("Telegram temporary upload lease close failed.");
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

    private record UploadWorkspace(Path directory, Path temporaryFile, FileChannel leaseChannel, FileLock lease) {}

    public record DocumentMetadata(String fileId, String fileName, String mimeType, long fileSize) {}

    public record Result(boolean accepted, String reply) {
        private static Result rejected(String reply) {
            return new Result(false, reply);
        }
    }
}
