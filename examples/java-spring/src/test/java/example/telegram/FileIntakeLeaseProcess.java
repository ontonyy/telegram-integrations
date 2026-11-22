package example.telegram;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class FileIntakeLeaseProcess {
    private FileIntakeLeaseProcess() {}

    public static void main(String[] args) throws Exception {
        Path leasePath = Path.of(args[0]);
        Path partialPath = Path.of(args[1]);
        Path readyPath = Path.of(args[2]);
        try (FileChannel channel = FileChannel.open(leasePath, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            Files.writeString(partialPath, "synthetic active upload");
            Files.writeString(readyPath, "lease acquired");
            System.in.read();
        }
    }
}
