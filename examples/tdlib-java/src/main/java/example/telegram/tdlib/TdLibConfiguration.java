package example.telegram.tdlib;

import java.nio.file.Path;

public record TdLibConfiguration(
        int apiId,
        String apiHash,
        Path databaseDirectory,
        byte[] databaseEncryptionKey) {

    public TdLibConfiguration {
        if (apiId <= 0) {
            throw new IllegalArgumentException("TDLib API ID must be positive");
        }
        if (apiHash == null || apiHash.isBlank()) {
            throw new IllegalArgumentException("TDLib API hash is required");
        }
        if (databaseDirectory == null || !databaseDirectory.isAbsolute()) {
            throw new IllegalArgumentException("TDLib database directory must be an absolute path");
        }
        if (databaseEncryptionKey == null || databaseEncryptionKey.length != 32) {
            throw new IllegalArgumentException("TDLib database key must contain exactly 32 bytes");
        }
        databaseEncryptionKey = databaseEncryptionKey.clone();
    }

    @Override
    public byte[] databaseEncryptionKey() {
        return databaseEncryptionKey.clone();
    }
}
