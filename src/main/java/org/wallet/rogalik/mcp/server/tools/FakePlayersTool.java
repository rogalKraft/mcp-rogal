package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.fakeplayer.FakePlayer;
import org.wallet.rogalik.mcp.server.fakeplayer.FakePlayerManager;
import org.wallet.rogalik.mcp.server.fakeplayer.FakePlayerRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public class FakePlayersTool implements McpTool {

    private final MCPConfig config;
    private final Supplier<MinecraftServer> serverSupplier;

    public FakePlayersTool(MCPConfig config, Supplier<MinecraftServer> serverSupplier) {
        this.config = config;
        this.serverSupplier = serverSupplier;
    }

    @Override
    public String name() {
        return "fake_players";
    }

    @Override
    public String description() {
        return "Spawn, despawn and list server-side players that have no client behind them.\n\n"
            + "They are ordinary players in every way that matters for testing: @a and @p select "
            + "them, scoreboards score them, /trigger works, they take damage, die, respawn and "
            + "change dimension. Nothing special-cases them, so behaviour you observe is the "
            + "behaviour real players get.\n\n"
            + "Their UUID is derived from the name in offline-mode form, so scoreboard values and "
            + "statistics survive a despawn and a later respawn under the same name.\n\n"
            + "despawn goes through the normal disconnect path, which fires a real leave event - "
            + "that is how you test grace periods and elimination-on-quit rules.\n\n"
            + "What they cannot do: press buttons in a dialog, because that needs a client. Use "
            + "click_dialog_button on the real player for those.\n\n"
            + "Actions: spawn, despawn, list.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .enumString("action", "What to do", true, "spawn", "despawn", "list")
            .string("name", "Player name, up to 16 letters, digits or underscores; required for "
                + "spawn and despawn", false)
            .nested("position", SchemaBuilder.blockPosition(null),
                "Where to place them; defaults to the real player's position", false)
            .string("dimension", "Dimension id such as minecraft:the_nether; defaults to the overworld", false)
            .enumString("gamemode", "Game mode to place them in", false,
                "survival", "creative", "adventure", "spectator")
            .number("yaw", "Horizontal facing", false)
            .number("pitch", "Vertical facing", false)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        if (!config.getFakePlayers().isEnabled()) {
            return MCPProtocol.createErrorResponse(
                "Fake players are disabled. Set fakePlayers.enabled to true in mcp.json.", null);
        }

        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            return MCPProtocol.createErrorResponse(
                "No world is loaded. Load a singleplayer world or start the server first.", null);
        }
        if (!arguments.has("action")) {
            return MCPProtocol.createErrorResponse("Missing required parameter: action", null);
        }

        String action = arguments.get("action").getAsString().trim().toLowerCase(Locale.ROOT);
        long timeoutMs = config.getServer().getRequestTimeoutMs();

        try {
            return switch (action) {
                case "list" -> list(server);
                case "spawn" -> spawn(server, arguments, timeoutMs);
                case "despawn" -> despawn(server, arguments, timeoutMs);
                default -> MCPProtocol.createErrorResponse(
                    "Unknown action '" + action + "'. Valid: spawn, despawn, list.", null);
            };
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return MCPProtocol.createErrorResponse(
                action + " failed: " + cause.getClass().getSimpleName() + ": " + cause.getMessage(), null);
        }
    }

    private JsonObject list(MinecraftServer server) {
        JsonArray fakes = new JsonArray();
        for (ServerPlayer player : FakePlayerManager.fakePlayers(server)) {
            fakes.add(describe(player));
        }

        JsonArray real = new JsonArray();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!FakePlayerRegistry.isFake(player)) {
                real.add(describe(player));
            }
        }

        JsonObject result = new JsonObject();
        result.add("fakePlayers", fakes);
        result.addProperty("fakeCount", fakes.size());
        result.add("realPlayers", real);
        result.addProperty("maxFakePlayers", config.getFakePlayers().getMaxCount());
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    private JsonObject spawn(MinecraftServer server, JsonObject arguments, long timeoutMs) throws Exception {
        if (!arguments.has("name")) {
            return MCPProtocol.createErrorResponse("Action 'spawn' requires 'name'", null);
        }
        String name = arguments.get("name").getAsString().trim();

        Optional<String> nameProblem = FakePlayerManager.validateName(name);
        if (nameProblem.isPresent()) {
            return MCPProtocol.createErrorResponse(nameProblem.get(), null);
        }
        if (FakePlayerManager.isOnline(server, name)) {
            return MCPProtocol.createErrorResponse(
                "A player called '" + name + "' is already online.", null);
        }

        List<ServerPlayer> existing = FakePlayerManager.fakePlayers(server);
        if (existing.size() >= config.getFakePlayers().getMaxCount()) {
            return MCPProtocol.createErrorResponse(
                "Already at the limit of " + config.getFakePlayers().getMaxCount()
                    + " fake players (fakePlayers.maxCount).", null);
        }

        ServerLevel level = FakePlayerManager.resolveLevel(server,
            arguments.has("dimension") ? arguments.get("dimension").getAsString() : null);
        if (level == null) {
            return MCPProtocol.createErrorResponse(
                "No dimension called '" + arguments.get("dimension").getAsString() + "'.", null);
        }

        GameType gameMode = FakePlayerManager.resolveGameMode(
            arguments.has("gamemode") ? arguments.get("gamemode").getAsString() : null,
            server.getDefaultGameType());
        if (gameMode == null) {
            return MCPProtocol.createErrorResponse(
                "Unknown gamemode. Valid: survival, creative, adventure, spectator.", null);
        }

        Vec3 position = FakePlayerManager.defaultPosition(server);
        if (arguments.has("position")) {
            JsonObject pos = arguments.getAsJsonObject("position");
            position = new Vec3(
                pos.get("x").getAsDouble() + 0.5,
                pos.get("y").getAsDouble(),
                pos.get("z").getAsDouble() + 0.5);
        }

        float yaw = arguments.has("yaw") ? arguments.get("yaw").getAsFloat() : 0f;
        float pitch = arguments.has("pitch") ? arguments.get("pitch").getAsFloat() : 0f;

        FakePlayer player = FakePlayerManager.spawn(
            server, name, level, position, yaw, pitch, gameMode, timeoutMs);

        JsonObject result = new JsonObject();
        result.addProperty("spawned", true);
        result.add("player", describe(player));
        result.addProperty("hint", "Address them by name in commands and selectors, exactly like a "
            + "real player. Use execute_commands with run_as to act at their permission level.");
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    private JsonObject despawn(MinecraftServer server, JsonObject arguments, long timeoutMs) throws Exception {
        if (!arguments.has("name")) {
            return MCPProtocol.createErrorResponse("Action 'despawn' requires 'name'", null);
        }
        String name = arguments.get("name").getAsString().trim();

        boolean removed = FakePlayerManager.despawn(server, name, timeoutMs);
        if (!removed) {
            return MCPProtocol.createErrorResponse(
                "No fake player called '" + name + "' is online. Use action 'list' to see them.", null);
        }

        JsonObject result = new JsonObject();
        result.addProperty("despawned", true);
        result.addProperty("name", name);
        result.addProperty("note", "They left the way a real player leaves, so any disconnect "
            + "handling in your datapack has run.");
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    private static JsonObject describe(ServerPlayer player) {
        JsonObject json = new JsonObject();
        json.addProperty("name", player.getName().getString());
        json.addProperty("uuid", player.getUUID().toString());
        json.addProperty("fake", FakePlayerRegistry.isFake(player));
        json.addProperty("dimension", player.level().dimension().identifier().toString());
        json.addProperty("gameMode", player.gameMode.getGameModeForPlayer().getName());
        json.addProperty("health", player.getHealth());

        JsonObject position = new JsonObject();
        position.addProperty("x", player.getX());
        position.addProperty("y", player.getY());
        position.addProperty("z", player.getZ());
        json.add("position", position);
        return json;
    }
}
