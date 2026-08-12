package org.wallet.rogalik.mcp.ui;

import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.input.InputDispatcher;
import org.wallet.rogalik.mcp.mixin.client.MinecraftServerAccessor;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.tools.McpTool;
import org.wallet.rogalik.mcp.server.tools.SchemaBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public class RejoinWorldTool implements McpTool {

    private static final long DEFAULT_TIMEOUT_MS = 120000L;
    private static final long POLL_INTERVAL_MS = 100L;

    private final MCPConfig config;

    public RejoinWorldTool(MCPConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "rejoin_world";
    }

    @Override
    public String description() {
        return "Quit to the title screen and load the same singleplayer world again.\n\n"
            + "Some datapack content is only read when a world loads, not on /reload: dynamic "
            + "registries such as dialog/, enchantment/ and worldgen/. Editing those and running "
            + "/reload leaves the old definitions in place, which looks exactly like the change "
            + "having no effect. This is the only way to pick them up.\n\n"
            + "Singleplayer only, and it takes as long as loading the world normally does.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .integer("timeout_ms", "How long to wait for the world to come back",
                false, (int) DEFAULT_TIMEOUT_MS)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null) {
            return MCPProtocol.createErrorResponse(
                "rejoin_world only works in singleplayer - there is no integrated server to restart.", null);
        }

        String levelId;
        try {
            levelId = ((MinecraftServerAccessor) server).mcp$storageSource().getLevelId();
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse(
                "Could not determine the save folder to reopen: " + e.getMessage(), null);
        }

        long timeoutMs = arguments.has("timeout_ms")
            ? Math.clamp(arguments.get("timeout_ms").getAsLong(), 5000L, 600000L)
            : DEFAULT_TIMEOUT_MS;
        long startedAt = System.currentTimeMillis();
        long deadline = startedAt + timeoutMs;

        try {
            InputDispatcher.onClientThread(client::disconnectWithSavingScreen)
                .get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);

            if (!awaitUntil(() -> Minecraft.getInstance().getSingleplayerServer() == null, deadline)) {
                return MCPProtocol.createErrorResponse(
                    "Timed out waiting for the world to unload.", null);
            }

            InputDispatcher.onClientThread(() -> {
                Minecraft mc = Minecraft.getInstance();
                mc.createWorldOpenFlows().openWorld(levelId, () -> mc.setScreenAndShow(new TitleScreen()));
            }).get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);

            if (!awaitUntil(() -> {
                Minecraft mc = Minecraft.getInstance();
                return mc.getSingleplayerServer() != null && mc.player != null && mc.level != null;
            }, deadline)) {
                return MCPProtocol.createErrorResponse(
                    "Timed out waiting for '" + levelId + "' to load again. Check get_logs - a broken "
                        + "datapack can stop a world from loading at all.", null);
            }
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("Failed to rejoin '" + levelId + "': " + e.getMessage(), null);
        }

        JsonObject result = new JsonObject();
        result.addProperty("world", levelId);
        result.addProperty("rejoined", true);
        result.addProperty("tookMs", System.currentTimeMillis() - startedAt);
        result.addProperty("hint", "Dynamic registries have been re-read. Check get_logs with "
            + "min_level WARN for anything that failed to load.");
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    private static boolean awaitUntil(BooleanSupplier condition, long deadline) throws InterruptedException {
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        return condition.getAsBoolean();
    }
}
