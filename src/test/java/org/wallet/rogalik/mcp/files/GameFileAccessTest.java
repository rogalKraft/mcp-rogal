package org.wallet.rogalik.mcp.files;

import com.google.gson.Gson;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class GameFileAccessTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path gameDir;

    private GameFileAccess access;

    @BeforeEach
    void setUp() throws IOException {
        MCPConfig config = GSON.fromJson("""
            {"files": {"allowedRoots": ["saves", "config"], "maxFileSizeBytes": 1024}}
            """, MCPConfig.class);
        access = new GameFileAccess(gameDir, config.getFiles());

        Files.createDirectories(gameDir.resolve("saves").resolve("world"));
        Files.createDirectories(gameDir.resolve("config"));
        Files.createDirectories(gameDir.resolve("secrets"));
        Files.writeString(gameDir.resolve("secrets").resolve("private.txt"), "do not read");
    }

    @Test
    public void resolvesPathsInsideAnAllowedRoot() {
        Path resolved = access.resolve("saves/world/level.dat");

        assertTrue(resolved.startsWith(gameDir));
        assertEquals("saves/world/level.dat", access.describe(resolved));
    }

    @Test
    public void parentTraversalOutOfTheGameDirectoryIsRefused() {
        GameFileAccess.AccessDeniedException e = assertThrows(
            GameFileAccess.AccessDeniedException.class,
            () -> access.resolve("saves/../../etc/passwd"));

        assertTrue(e.getMessage().contains("outside the game directory"), e.getMessage());
    }

    @Test
    public void traversalThatLandsBackInsideButOutsideAnAllowedRootIsRefused() {
        // Stays under the game directory, so the traversal check alone would pass it.
        GameFileAccess.AccessDeniedException e = assertThrows(
            GameFileAccess.AccessDeniedException.class,
            () -> access.resolve("saves/../secrets/private.txt"));

        assertTrue(e.getMessage().contains("outside the allowed directories"), e.getMessage());
    }

    @Test
    public void directoriesOutsideTheAllowListAreRefused() {
        assertThrows(GameFileAccess.AccessDeniedException.class,
            () -> access.resolve("secrets/private.txt"));
        assertThrows(GameFileAccess.AccessDeniedException.class,
            () -> access.resolve("mods/something.jar"));
    }

    @Test
    public void absolutePathsAreRefused() {
        GameFileAccess.AccessDeniedException e = assertThrows(
            GameFileAccess.AccessDeniedException.class,
            () -> access.resolve(gameDir.resolve("saves").toString()));

        assertTrue(e.getMessage().contains("Absolute paths"), e.getMessage());
    }

    @Test
    public void theGameDirectoryItselfIsNotWritable() {
        assertThrows(GameFileAccess.AccessDeniedException.class, () -> access.resolve("."));
    }

    @Test
    public void emptyPathsAreRefused() {
        assertThrows(GameFileAccess.AccessDeniedException.class, () -> access.resolve(""));
        assertThrows(GameFileAccess.AccessDeniedException.class, () -> access.resolve("   "));
        assertThrows(GameFileAccess.AccessDeniedException.class, () -> access.resolve(null));
    }

    @Test
    public void aSymlinkPointingOutsideIsRefused() throws IOException {
        Path outside = Files.createTempDirectory("mcp-outside");
        Path target = outside.resolve("stolen.txt");
        Files.writeString(target, "secret");

        Path link = gameDir.resolve("saves").resolve("escape");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException e) {
            // Creating symlinks needs privileges on Windows; nothing to assert if we cannot.
            return;
        }

        GameFileAccess.AccessDeniedException denied = assertThrows(
            GameFileAccess.AccessDeniedException.class,
            () -> access.resolve("saves/escape/stolen.txt"));
        assertTrue(denied.getMessage().contains("outside"), denied.getMessage());
    }

    @Test
    public void nonExistentPathsInsideAnAllowedRootResolveSoFilesCanBeCreated() {
        Path resolved = access.resolve("saves/world/datapacks/new/pack.mcmeta");

        assertTrue(resolved.startsWith(gameDir.resolve("saves")));
    }

    @Test
    public void sizeLimitIsEnforced() {
        assertDoesNotThrow(() -> access.checkSize(1024));

        GameFileAccess.AccessDeniedException e = assertThrows(
            GameFileAccess.AccessDeniedException.class, () -> access.checkSize(1025));
        assertTrue(e.getMessage().contains("over the"), e.getMessage());
    }

    @Test
    public void describeUsesForwardSlashesRegardlessOfPlatform() {
        String described = access.describe(access.resolve("saves/world/level.dat"));

        assertFalse(described.contains("\\"), "Paths are reported with forward slashes: " + described);
    }

    @Test
    public void allowListMatchingIgnoresCase() {
        assertDoesNotThrow(() -> access.resolve("Saves/world/level.dat"));
    }
}
