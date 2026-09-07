package dev.rtpbuddy.util;

import dev.rtpbuddy.RTPBuddy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Crash-safe file writing. Every persistent file is written to a sibling
 * {@code .tmp} first and then moved into place, so a crash mid-write can never
 * truncate the authoritative sample file.
 */
public final class FileOps {

    private FileOps() {
    }

    public static void writeAtomic(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String readOrNull(Path source) {
        try {
            if (!Files.isRegularFile(source)) {
                return null;
            }
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            RTPBuddy.LOGGER.warn("[RTPBuddy] could not read {}: {}", source, e.toString());
            return null;
        }
    }

    /**
     * Recovers a {@code .tmp} left behind by a crash: if the real file is missing
     * but the temp file parses as non-empty content, promote it.
     */
    public static void recoverStrayTemp(Path target) {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            if (Files.isRegularFile(tmp) && !Files.isRegularFile(target) && Files.size(tmp) > 0) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                RTPBuddy.LOGGER.warn("[RTPBuddy] recovered {} from interrupted write", target.getFileName());
            }
        } catch (IOException e) {
            RTPBuddy.LOGGER.warn("[RTPBuddy] temp recovery failed for {}: {}", target, e.toString());
        }
    }
}
