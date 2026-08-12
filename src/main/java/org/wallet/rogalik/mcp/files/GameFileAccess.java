package org.wallet.rogalik.mcp.files;

import org.wallet.rogalik.mcp.config.MCPConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/**
 * Confines file access to an allow-listed set of directories under the game folder.
 *
 * <p>The check resolves the path first and compares afterwards, because {@code ..} segments and
 * symlinks only reveal where they actually point once resolved — validating the raw string is
 * exactly the mistake that makes a sandbox porous.
 */
public class GameFileAccess {

    private final Path gameDir;
    private final List<String> allowedRoots;
    private final long maxFileSizeBytes;
    private final boolean allowDelete;

    public GameFileAccess(Path gameDir, MCPConfig.FilesConfig config) {
        this.gameDir = gameDir.toAbsolutePath().normalize();
        this.allowedRoots = config.getAllowedRoots();
        this.maxFileSizeBytes = config.getMaxFileSizeBytes();
        this.allowDelete = config.isAllowDelete();
    }

    /** Refusal to touch a path, with the reason. */
    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String message) {
            super(message);
        }
    }

    public Path gameDir() {
        return gameDir;
    }

    public boolean isDeleteAllowed() {
        return allowDelete;
    }

    public long maxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public List<String> allowedRoots() {
        return allowedRoots;
    }

    /**
     * Resolves a game-relative path and verifies it stays inside an allowed subtree.
     *
     * @throws AccessDeniedException when the path escapes the sandbox
     */
    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new AccessDeniedException("Path must not be empty");
        }

        Path requested;
        try {
            requested = Path.of(relativePath);
        } catch (InvalidPathException e) {
            throw new AccessDeniedException("'" + relativePath + "' is not a usable path: " + e.getReason());
        }

        if (requested.isAbsolute()) {
            throw new AccessDeniedException(
                "Absolute paths are not accepted; give a path relative to the game directory");
        }

        Path candidate = gameDir.resolve(requested).normalize();

        // normalize() collapses "..", so this catches traversal out of the game folder.
        if (!candidate.startsWith(gameDir)) {
            throw new AccessDeniedException("'" + relativePath + "' resolves outside the game directory");
        }

        // A symlink can point anywhere, so re-check the real location for anything that exists.
        Path real = candidate;
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            try {
                real = candidate.toRealPath();
            } catch (IOException e) {
                throw new AccessDeniedException("Could not resolve '" + relativePath + "': " + e.getMessage());
            }
            if (!real.startsWith(gameDir)) {
                throw new AccessDeniedException(
                    "'" + relativePath + "' is a link pointing outside the game directory");
            }
        }

        if (!isUnderAllowedRoot(real)) {
            throw new AccessDeniedException(
                "'" + relativePath + "' is outside the allowed directories: "
                    + String.join(", ", allowedRoots)
                    + ". Adjust files.allowedRoots in mcp.json to widen this.");
        }

        return candidate;
    }

    private boolean isUnderAllowedRoot(Path resolved) {
        Path relative = gameDir.relativize(resolved);
        if (relative.getNameCount() == 0) {
            // The game directory itself is listable but holds nothing writable directly.
            return false;
        }
        String first = relative.getName(0).toString();
        return allowedRoots.stream().anyMatch(root -> root.equalsIgnoreCase(first));
    }

    /** The path as the caller should see it: relative to the game directory, with forward slashes. */
    public String describe(Path path) {
        return gameDir.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    public void checkSize(long bytes) {
        if (bytes > maxFileSizeBytes) {
            throw new AccessDeniedException(
                "File is " + bytes + " bytes, over the " + maxFileSizeBytes + " byte limit "
                    + "(files.maxFileSizeBytes)");
        }
    }
}
