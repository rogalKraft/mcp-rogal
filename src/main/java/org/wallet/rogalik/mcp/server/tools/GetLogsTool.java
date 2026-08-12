package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.logs.LogEntry;
import org.wallet.rogalik.mcp.logs.LogQueryParser;
import org.wallet.rogalik.mcp.logs.LogRingBuffer;
import org.wallet.rogalik.mcp.logs.McpLogs;
import org.wallet.rogalik.mcp.server.MCPProtocol;

public class GetLogsTool implements McpTool {

    private static final int DEFAULT_LIMIT = 200;

    @Override
    public String name() {
        return "get_logs";
    }

    @Override
    public String description() {
        return "Read the game log, including in-game chat and command feedback.\n\n"
            + "Cursor-based: every entry has an id, and the response carries nextId. Pass that back "
            + "as since_id on the following call to receive only what arrived since - polling does "
            + "not re-read what you already have.\n\n"
            + "The response reports 'missed' when the buffer overflowed past your cursor, so a gap "
            + "is visible rather than silent.\n\n"
            + "Sources: 'game' is anything written to the log, 'chat' is in-game chat and command "
            + "output, which never reaches the log file at all.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .integer("since_id", "Return entries with an id at or above this. Use nextId from the "
                + "previous response; omit or pass 0 to read from the start of the buffer", false)
            .integer("tail", "Return only the newest N matching entries", false)
            .enumString("min_level", "Minimum severity to include", false,
                "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")
            .string("pattern", "Case-insensitive regular expression matched against the message "
                + "and any stack trace", false)
            .string("logger_pattern", "Case-insensitive regular expression matched against the logger name", false)
            .arrayOf("sources", "string", "Restrict to 'game' and/or 'chat'; defaults to both", false)
            .integer("limit", "Maximum entries to return (1-2000)", false, DEFAULT_LIMIT)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        LogRingBuffer buffer = McpLogs.buffer();
        if (buffer == null) {
            return MCPProtocol.createErrorResponse(
                "Log capture is not active. Check that logs.enabled is true in mcp.json.", null);
        }

        LogRingBuffer.Query query;
        try {
            query = LogQueryParser.parse(arguments, DEFAULT_LIMIT);
        } catch (LogQueryParser.InvalidQueryException e) {
            return MCPProtocol.createErrorResponse(e.getMessage(), null);
        }

        LogRingBuffer.Result result = buffer.query(query);

        JsonArray entries = new JsonArray();
        for (LogEntry entry : result.entries()) {
            entries.add(entry.toJson());
        }

        JsonObject response = new JsonObject();
        response.add("entries", entries);
        response.addProperty("count", entries.size());
        response.addProperty("nextId", result.nextId());
        response.addProperty("missed", result.missed());
        response.addProperty("droppedTotal", result.droppedTotal());
        response.addProperty("bufferedEntries", buffer.size());
        if (result.missed() > 0) {
            response.addProperty("warning", result.missed() + " entries were evicted from the buffer "
                + "before this read. Poll more often or raise logs.bufferSize to avoid gaps.");
        }
        return MCPProtocol.createSuccessResponse(response.toString());
    }
}
