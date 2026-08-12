package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.platform.Platform;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public class WorldInfoTool implements McpTool {

    private final Supplier<MinecraftServer> serverSupplier;

    public WorldInfoTool(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = serverSupplier;
    }

    @Override
    public String name() {
        return "get_world_info";
    }

    @Override
    public String description() {
        return "Report the loaded world: its name, dimensions, tick count, where its datapack "
            + "folder lives, and the pack formats this game version expects.\n\n"
            + "Call this before writing datapack files. The datapacks directory sits inside the "
            + "save folder, and the save folder name is not the same as the world's display name "
            + "once a world has been renamed or copied.\n\n"
            + "The pack_format matters just as much: get it wrong and the game rejects the pack "
            + "with only a vague 'Error reading pack metadata' warning, while every function in it "
            + "silently does nothing.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.empty();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            return MCPProtocol.createErrorResponse(
                "No world is loaded. Load a singleplayer world or start the server first.", null);
        }

        JsonObject result = new JsonObject();
        result.addProperty("tickCount", server.getTickCount());

        // Pack formats change every version and a wrong one makes the game reject the pack with
        // nothing but "Error reading pack metadata" - so report the values this build expects.
        result.addProperty("dataPackFormat", SharedConstants.DATA_PACK_FORMAT_MAJOR);
        result.addProperty("dataPackFormatMinor", SharedConstants.DATA_PACK_FORMAT_MINOR);
        result.addProperty("resourcePackFormat", SharedConstants.RESOURCE_PACK_FORMAT_MAJOR);
        result.addProperty("packMcmetaExample",
            "{\"pack\":{\"pack_format\":" + SharedConstants.DATA_PACK_FORMAT_MAJOR
                + ",\"description\":\"my pack\"}}");

        try {
            result.addProperty("worldName", server.getWorldData().getLevelName());
        } catch (Exception e) {
            result.addProperty("worldName", "unknown");
        }

        JsonArray dimensions = new JsonArray();
        for (ServerLevel level : server.getAllLevels()) {
            JsonObject dimension = new JsonObject();
            dimension.addProperty("id", level.dimension().identifier().toString());
            dimension.addProperty("gameTime", level.getGameTime());
            dimension.addProperty("players", level.players().size());
            dimensions.add(dimension);
        }
        result.add("dimensions", dimensions);

        Path gameDir = Platform.get().gameDir();
        result.addProperty("gameDirectory", gameDir.toString());

        // The overworld's directory is the save root, which is where datapacks/ sits.
        Path saveRoot = findSaveRoot(server);
        if (saveRoot != null) {
            String relative = relativize(gameDir, saveRoot);
            result.addProperty("saveDirectory", relative);
            result.addProperty("datapacksDirectory", relative + "/datapacks");
            result.addProperty("hint", "Write datapacks under " + relative + "/datapacks/<pack>/, "
                + "then call reload_datapacks.");
        } else {
            result.addProperty("note", "Could not locate the save directory on disk; on a dedicated "
                + "server datapacks usually live in <level-name>/datapacks.");
        }

        return MCPProtocol.createSuccessResponse(result.toString());
    }

    /** Walks up from a level's storage path to the save root that contains level.dat. */
    private static Path findSaveRoot(MinecraftServer server) {
        try {
            Path candidate = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .toAbsolutePath().normalize();
            return Files.isDirectory(candidate) ? candidate : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String relativize(Path gameDir, Path target) {
        try {
            return gameDir.toAbsolutePath().normalize().relativize(target).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return target.toString().replace('\\', '/');
        }
    }
}
