package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.files.GameFileAccess;
import org.wallet.rogalik.mcp.platform.Platform;
import org.wallet.rogalik.mcp.server.MCPProtocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Sandboxed file access under the game directory, for editing datapacks in place. */
public class FilesTool implements McpTool {

    private final MCPConfig config;

    public FilesTool(MCPConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "files";
    }

    @Override
    public String description() {
        return "Read, write and list files inside the game directory.\n\n"
            + "This is what closes the datapack loop: write a function or loot table into "
            + "saves/<world>/datapacks/..., run /reload, then read the log to see whether it "
            + "loaded - without leaving the game.\n\n"
            + "Use get_world_info to find the current save folder.\n\n"
            + "Access is confined to an allow-listed set of directories; anything resolving "
            + "outside them, including through .. or a symlink, is refused.\n\n"
            + "Actions: list, read, write, mkdir, delete (delete requires files.allowDelete).";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .enumString("action", "What to do", true, "list", "read", "write", "mkdir", "delete")
            .string("path", "Path relative to the game directory, e.g. 'saves/my-world/datapacks'", true)
            .string("content", "File contents; required for write", false)
            .bool("recursive", "For list, descend into subdirectories", false, false)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        if (!config.getFiles().isEnabled()) {
            return MCPProtocol.createErrorResponse(
                "File access is disabled. Set files.enabled to true in mcp.json.", null);
        }
        if (!arguments.has("action") || !arguments.has("path")) {
            return MCPProtocol.createErrorResponse("Both 'action' and 'path' are required", null);
        }

        GameFileAccess access = new GameFileAccess(Platform.get().gameDir(), config.getFiles());
        String action = arguments.get("action").getAsString().trim().toLowerCase(Locale.ROOT);

        try {
            Path target = access.resolve(arguments.get("path").getAsString());
            return switch (action) {
                case "list" -> list(access, target,
                    arguments.has("recursive") && arguments.get("recursive").getAsBoolean());
                case "read" -> read(access, target);
                case "write" -> write(access, target, arguments);
                case "mkdir" -> mkdir(access, target);
                case "delete" -> delete(access, target);
                default -> MCPProtocol.createErrorResponse(
                    "Unknown action '" + action + "'. Valid: list, read, write, mkdir, delete.", null);
            };
        } catch (GameFileAccess.AccessDeniedException e) {
            return MCPProtocol.createErrorResponse("Refused: " + e.getMessage(), null);
        } catch (IOException e) {
            return MCPProtocol.createErrorResponse(
                action + " failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), null);
        }
    }

    private JsonObject list(GameFileAccess access, Path target, boolean recursive) throws IOException {
        if (!Files.exists(target)) {
            return MCPProtocol.createErrorResponse("'" + access.describe(target) + "' does not exist", null);
        }
        if (!Files.isDirectory(target)) {
            return MCPProtocol.createErrorResponse(
                "'" + access.describe(target) + "' is a file; use action 'read'", null);
        }

        JsonArray entries = new JsonArray();
        try (Stream<Path> stream = recursive ? Files.walk(target, 8) : Files.list(target)) {
            List<Path> paths = stream
                .filter(path -> !path.equals(target))
                .sorted(Comparator.comparing(Path::toString))
                .limit(2000)
                .toList();

            for (Path path : paths) {
                JsonObject entry = new JsonObject();
                entry.addProperty("path", access.describe(path));
                entry.addProperty("directory", Files.isDirectory(path));
                if (Files.isRegularFile(path)) {
                    entry.addProperty("bytes", Files.size(path));
                }
                entries.add(entry);
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("path", access.describe(target));
        result.add("entries", entries);
        result.addProperty("count", entries.size());
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    private JsonObject read(GameFileAccess access, Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return MCPProtocol.createErrorResponse(
                "'" + access.describe(target) + "' is not a readable file", null);
        }
        access.checkSize(Files.size(target));

        JsonObject result = new JsonObject();
        result.addProperty("path", access.describe(target));
        result.addProperty("bytes", Files.size(target));
        result.addProperty("content", Files.readString(target, StandardCharsets.UTF_8));
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    private JsonObject write(GameFileAccess access, Path target, JsonObject arguments) throws IOException {
        if (!arguments.has("content")) {
            return MCPProtocol.createErrorResponse("Action 'write' requires 'content'", null);
        }
        String content = arguments.get("content").getAsString();
        access.checkSize(content.getBytes(StandardCharsets.UTF_8).length);

        boolean existed = Files.exists(target);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content, StandardCharsets.UTF_8);

        JsonObject result = new JsonObject();
        result.addProperty("path", access.describe(target));
        result.addProperty("bytes", Files.size(target));
        result.addProperty("overwrote", existed);
        result.addProperty("hint", "For a datapack change, follow with reload_datapacks, then "
            + "wait_for_log to catch a load error.");
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    private JsonObject mkdir(GameFileAccess access, Path target) throws IOException {
        Files.createDirectories(target);
        JsonObject result = new JsonObject();
        result.addProperty("path", access.describe(target));
        result.addProperty("created", true);
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    private JsonObject delete(GameFileAccess access, Path target) throws IOException {
        if (!access.isDeleteAllowed()) {
            return MCPProtocol.createErrorResponse(
                "Deleting is disabled. Set files.allowDelete to true in mcp.json to permit it.", null);
        }
        if (Files.isDirectory(target)) {
            return MCPProtocol.createErrorResponse(
                "Refusing to delete a directory; remove files individually.", null);
        }
        boolean deleted = Files.deleteIfExists(target);

        JsonObject result = new JsonObject();
        result.addProperty("path", access.describe(target));
        result.addProperty("deleted", deleted);
        return MCPProtocol.createSuccessResponse(result.toString());
    }
}
