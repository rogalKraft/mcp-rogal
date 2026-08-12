package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.exec.ServerTicks;
import net.minecraft.server.MinecraftServer;

import java.util.function.Supplier;

public class AdvanceTicksTool implements McpTool {

    /** Guards against a request that would outlast the HTTP timeout anyway. */
    private static final int MAX_TICKS = 12000;

    private final MCPConfig config;
    private final Supplier<MinecraftServer> serverSupplier;

    public AdvanceTicksTool(MCPConfig config, Supplier<MinecraftServer> serverSupplier) {
        this.config = config;
        this.serverSupplier = serverSupplier;
    }

    @Override
    public String name() {
        return "advance_ticks";
    }

    @Override
    public String description() {
        return "Wait for the server to advance a number of ticks, then return.\n\n"
            + "Use this between writing state and reading it back: within a single tick a read "
            + "still sees the value from before the write, so a scoreboard or data check issued "
            + "immediately after the command that set it reports the old number.\n\n"
            + "20 ticks is one second at normal speed. To land on a specific phase of a repeating "
            + "datapack timer, advance to it rather than sleeping in wall-clock time.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .integer("ticks", "Number of server ticks to wait for (1-" + MAX_TICKS + ")", true)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            return MCPProtocol.createErrorResponse(
                "No server is running. Load a world (singleplayer) or start the server first.", null);
        }

        if (!arguments.has("ticks")) {
            return MCPProtocol.createErrorResponse("Missing required parameter: ticks", null);
        }

        int requested = Math.clamp(arguments.get("ticks").getAsInt(), 0, MAX_TICKS);
        int startTick = server.getTickCount();
        int advanced = ServerTicks.await(server, requested, config.getServer().getRequestTimeoutMs());

        JsonObject result = new JsonObject();
        result.addProperty("requestedTicks", requested);
        result.addProperty("ticksAdvanced", advanced);
        result.addProperty("startTick", startTick);
        result.addProperty("currentTick", server.getTickCount());
        if (advanced < requested) {
            result.addProperty("warning",
                "Timed out before reaching the requested tick count. The server may be frozen "
                    + "(/tick freeze) or heavily lagging.");
        }
        return MCPProtocol.createSuccessResponse(result.toString());
    }
}
