package org.wallet.rogalik.mcp.logs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.logs.LogEntry.LogLevel;
import org.wallet.rogalik.mcp.logs.LogEntry.LogSource;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Turns tool arguments into a {@link LogRingBuffer.Query}, shared by get_logs and wait_for_log. */
public final class LogQueryParser {

    private LogQueryParser() {
    }

    /** Thrown for user-supplied input that cannot be honoured, so the tool can report it cleanly. */
    public static class InvalidQueryException extends RuntimeException {
        public InvalidQueryException(String message) {
            super(message);
        }
    }

    public static LogRingBuffer.Query parse(JsonObject arguments, int defaultLimit) {
        long sinceId = arguments.has("since_id") ? Math.max(0, arguments.get("since_id").getAsLong()) : 0;

        LogLevel minLevel = arguments.has("min_level")
            ? LogLevel.fromName(arguments.get("min_level").getAsString())
            : LogLevel.TRACE;

        Pattern loggerPattern = compile(arguments, "logger_pattern");
        Pattern messagePattern = compile(arguments, "pattern", "message_pattern");

        Set<LogSource> sources = parseSources(arguments);

        int limit = arguments.has("limit")
            ? Math.clamp(arguments.get("limit").getAsInt(), 1, 2000)
            : defaultLimit;
        // "tail" is the familiar name for the same thing when reading from the end.
        if (arguments.has("tail")) {
            limit = Math.clamp(arguments.get("tail").getAsInt(), 1, 2000);
        }

        return new LogRingBuffer.Query(sinceId, minLevel, loggerPattern, messagePattern, sources, limit);
    }

    private static Set<LogSource> parseSources(JsonObject arguments) {
        if (!arguments.has("sources") || !arguments.get("sources").isJsonArray()) {
            return EnumSet.allOf(LogSource.class);
        }

        JsonArray array = arguments.getAsJsonArray("sources");
        EnumSet<LogSource> sources = EnumSet.noneOf(LogSource.class);
        for (JsonElement element : array) {
            String name = element.getAsString().trim().toUpperCase();
            try {
                sources.add(LogSource.valueOf(name));
            } catch (IllegalArgumentException e) {
                throw new InvalidQueryException(
                    "Unknown log source '" + element.getAsString() + "'. Valid sources: game, chat");
            }
        }
        return sources.isEmpty() ? EnumSet.allOf(LogSource.class) : sources;
    }

    private static Pattern compile(JsonObject arguments, String... keys) {
        for (String key : keys) {
            if (arguments.has(key) && !arguments.get(key).isJsonNull()) {
                String raw = arguments.get(key).getAsString();
                if (raw.isEmpty()) {
                    return null;
                }
                try {
                    return Pattern.compile(raw, Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException e) {
                    throw new InvalidQueryException(
                        "Invalid regular expression for '" + key + "': " + e.getDescription());
                }
            }
        }
        return null;
    }
}
