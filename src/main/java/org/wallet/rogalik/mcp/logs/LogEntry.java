package org.wallet.rogalik.mcp.logs;

import com.google.gson.JsonObject;

/**
 * One captured line, identified by a monotonically increasing id so callers can poll for
 * everything since their last read instead of re-reading the whole buffer.
 */
public record LogEntry(
    long id,
    long timestampMs,
    LogLevel level,
    String logger,
    String thread,
    LogSource source,
    String message,
    String throwable
) {

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("ts", timestampMs);
        json.addProperty("level", level.name());
        json.addProperty("logger", logger);
        json.addProperty("thread", thread);
        json.addProperty("source", source.name().toLowerCase());
        json.addProperty("message", message);
        if (throwable != null && !throwable.isEmpty()) {
            json.addProperty("throwable", throwable);
        }
        return json;
    }

    /** Where a line came from. */
    public enum LogSource {
        /** Anything written through the game's logger. */
        GAME,
        /** In-game chat and command feedback, which never reaches the log file. */
        CHAT
    }

    /**
     * Severity, ordered so filtering by a minimum level is a comparison.
     *
     * <p>Kept separate from Log4j's own {@code Level} so the buffer and its tests do not depend
     * on the logging backend.
     */
    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL;

        public boolean isAtLeast(LogLevel minimum) {
            return ordinal() >= minimum.ordinal();
        }

        /** Maps a Log4j level name onto this scale, defaulting to INFO for anything unrecognised. */
        public static LogLevel fromName(String name) {
            if (name == null) {
                return INFO;
            }
            return switch (name.trim().toUpperCase()) {
                case "TRACE", "ALL" -> TRACE;
                case "DEBUG" -> DEBUG;
                case "WARN", "WARNING" -> WARN;
                case "ERROR", "SEVERE" -> ERROR;
                case "FATAL" -> FATAL;
                default -> INFO;
            };
        }
    }
}
