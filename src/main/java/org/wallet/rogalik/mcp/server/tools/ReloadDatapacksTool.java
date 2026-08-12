package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.logs.LogRingBuffer;
import org.wallet.rogalik.mcp.logs.McpLogs;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.exec.CommandOutcome;
import org.wallet.rogalik.mcp.server.exec.CommandRunner;
import net.minecraft.server.MinecraftServer;

import java.util.function.Supplier;

public class ReloadDatapacksTool implements McpTool {

    private final MCPConfig config;
    private final Supplier<MinecraftServer> serverSupplier;

    public ReloadDatapacksTool(MCPConfig config, Supplier<MinecraftServer> serverSupplier) {
        this.config = config;
        this.serverSupplier = serverSupplier;
    }

    @Override
    public String name() {
        return "reload_datapacks";
    }

    @Override
    public String description() {
        return "Reload datapacks - the equivalent of running /reload.\n\n"
            + "Picks up changed functions, loot tables, recipes, advancements and tags. It does "
            + "NOT re-read dynamic registries such as dialog/, enchantment/ and worldgen/; those "
            + "load only when a world loads, so use rejoin_world for them.\n\n"
            + "The response includes the log lines the reload produced, because a pack that fails "
            + "to load reports it there and nowhere else - the command itself still succeeds.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .bool("include_log", "Include log lines produced by the reload", false, true)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            return MCPProtocol.createErrorResponse(
                "No world is loaded, so there is nothing to reload.", null);
        }

        LogRingBuffer buffer = McpLogs.buffer();
        long cursor = buffer == null ? 0 : buffer.nextId();

        CommandRunner runner = new CommandRunner(config, serverSupplier);
        // /reload is not in the command allow list and does not need to be; it changes no world state.
        CommandOutcome outcome = runner.runOne(server, "reload", CommandRunner.RunAs.CONSOLE);

        JsonObject result = new JsonObject();
        result.addProperty("success", outcome.success());
        result.addProperty("summary", outcome.summary());
        if (outcome.error() != null) {
            result.addProperty("error", outcome.error());
        }
        JsonArray messages = new JsonArray();
        outcome.messages().forEach(messages::add);
        result.add("messages", messages);

        boolean includeLog = !arguments.has("include_log") || arguments.get("include_log").getAsBoolean();
        if (includeLog && buffer != null) {
            JsonArray logLines = new JsonArray();
            LogRingBuffer.Query query = new LogRingBuffer.Query(
                cursor, org.wallet.rogalik.mcp.logs.LogEntry.LogLevel.TRACE, null, null,
                java.util.EnumSet.allOf(org.wallet.rogalik.mcp.logs.LogEntry.LogSource.class), 200);
            LogRingBuffer.Result logs = buffer.query(query);
            logs.entries().forEach(entry -> logLines.add(entry.toJson()));
            result.add("log", logLines);
            result.addProperty("nextLogId", logs.nextId());

            boolean sawProblem = logs.entries().stream().anyMatch(entry ->
                entry.level().isAtLeast(org.wallet.rogalik.mcp.logs.LogEntry.LogLevel.WARN));
            result.addProperty("logHasWarnings", sawProblem);
            if (sawProblem) {
                result.addProperty("hint", "The reload logged warnings or errors - a pack likely "
                    + "failed to load. Read the log entries above.");
            }
        }
        return MCPProtocol.createSuccessResponse(result.toString());
    }
}
