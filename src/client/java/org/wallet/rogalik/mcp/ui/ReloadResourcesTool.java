package org.wallet.rogalik.mcp.ui;

import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.input.InputDispatcher;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.tools.McpTool;
import org.wallet.rogalik.mcp.server.tools.SchemaBuilder;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ReloadResourcesTool implements McpTool {

    /** Resource reloads rebuild every atlas and model, so they outlast a normal request timeout. */
    private static final long DEFAULT_TIMEOUT_MS = 120000L;

    private final MCPConfig config;

    public ReloadResourcesTool(MCPConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "reload_resources";
    }

    @Override
    public String description() {
        return "Reload client resource packs - the equivalent of pressing F3+T.\n\n"
            + "Needed to pick up changed textures, models, sounds and language files. There is no "
            + "command for it, so without this tool a resource pack change cannot be tested at all.\n\n"
            + "This does NOT reload datapacks; use /reload for those, and rejoin_world for dynamic "
            + "registries such as dialogs and enchantments, which only load when a world loads.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .integer("timeout_ms", "How long to wait for the reload to finish", false, (int) DEFAULT_TIMEOUT_MS)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        long timeoutMs = arguments.has("timeout_ms")
            ? Math.clamp(arguments.get("timeout_ms").getAsLong(), 1000L, 600000L)
            : DEFAULT_TIMEOUT_MS;

        long startedAt = System.currentTimeMillis();
        CompletableFuture<Void> reload = new CompletableFuture<>();

        try {
            InputDispatcher.onClientThread(() ->
                Minecraft.getInstance().reloadResourcePacks().whenComplete((ignored, error) -> {
                    if (error != null) {
                        reload.completeExceptionally(error);
                    } else {
                        reload.complete(null);
                    }
                })
            ).get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);

            reload.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return MCPProtocol.createErrorResponse(
                "Resource reload failed: " + cause.getMessage()
                    + ". Check get_logs for the pack error that caused it.", null);
        }

        JsonObject result = new JsonObject();
        result.addProperty("reloaded", true);
        result.addProperty("tookMs", System.currentTimeMillis() - startedAt);
        result.addProperty("hint", "Use get_open_screen to confirm new translation keys render, or "
            + "get_logs with min_level WARN to catch pack errors.");
        return MCPProtocol.createSuccessResponse(result.toString());
    }
}
