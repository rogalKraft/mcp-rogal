package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.logs.LogEntry;
import org.wallet.rogalik.mcp.logs.LogQueryParser;
import org.wallet.rogalik.mcp.logs.LogRingBuffer;
import org.wallet.rogalik.mcp.logs.McpLogs;
import org.wallet.rogalik.mcp.server.MCPProtocol;

public class WaitForLogTool implements McpTool {

    private static final int CONTEXT_BEFORE = 5;
    private static final int CONTEXT_AFTER = 3;

    private final MCPConfig config;

    public WaitForLogTool(MCPConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "wait_for_log";
    }

    @Override
    public String description() {
        return "Block until a log line matching a pattern appears, or the timeout elapses.\n\n"
            + "This is the live half of log access: trigger something, then wait for the line that "
            + "tells you whether it worked, instead of polling get_logs in a loop.\n\n"
            + "Typical use after reloading a datapack:\n"
            + "  wait_for_log(pattern: \"Failed to load|Couldn't load|error\", min_level: \"WARN\")\n\n"
            + "Entries already in the buffer at or after since_id are checked before waiting, so a "
            + "line that landed between your last read and this call is not missed. On a match the "
            + "surrounding entries come back too, which is usually where the cause is.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .string("pattern", "Case-insensitive regular expression to wait for, matched against "
                + "the message and any stack trace", true)
            .integer("timeout_ms", "How long to wait before giving up", false, 15000)
            .enumString("min_level", "Minimum severity to consider a match", false,
                "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")
            .string("logger_pattern", "Restrict matching to loggers whose name matches this regex", false)
            .integer("since_id", "Start looking from this entry id; use nextId from a previous "
                + "get_logs so nothing between the two calls is missed", false)
            .arrayOf("sources", "string", "Restrict to 'game' and/or 'chat'; defaults to both", false)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        LogRingBuffer buffer = McpLogs.buffer();
        if (buffer == null) {
            return MCPProtocol.createErrorResponse(
                "Log capture is not active. Check that logs.enabled is true in mcp.json.", null);
        }
        if (!arguments.has("pattern")) {
            return MCPProtocol.createErrorResponse("Missing required parameter: pattern", null);
        }

        LogRingBuffer.Query query;
        try {
            query = LogQueryParser.parse(arguments, 1);
        } catch (LogQueryParser.InvalidQueryException e) {
            return MCPProtocol.createErrorResponse(e.getMessage(), null);
        }

        long requested = arguments.has("timeout_ms") ? arguments.get("timeout_ms").getAsLong() : 15000L;
        long timeoutMs = Math.clamp(requested, 100L, config.getLogs().getMaxWaitMs());

        long startedAt = System.currentTimeMillis();
        LogEntry match;
        try {
            match = buffer.awaitMatch(query, timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return MCPProtocol.createErrorResponse("Interrupted while waiting for a log match", null);
        }

        JsonObject response = new JsonObject();
        response.addProperty("waitedMs", System.currentTimeMillis() - startedAt);
        response.addProperty("timeoutMs", timeoutMs);
        response.addProperty("nextId", buffer.nextId());

        if (match == null) {
            response.addProperty("matched", false);
            response.addProperty("hint", "Nothing matched within the timeout. The pattern may be wrong, "
                + "the event may not have happened, or min_level may be filtering it out.");
            return MCPProtocol.createSuccessResponse(response.toString());
        }

        response.addProperty("matched", true);
        response.add("entry", match.toJson());

        JsonArray context = new JsonArray();
        for (LogEntry entry : buffer.contextAround(match.id(), CONTEXT_BEFORE, CONTEXT_AFTER)) {
            context.add(entry.toJson());
        }
        response.add("context", context);

        return MCPProtocol.createSuccessResponse(response.toString());
    }
}
