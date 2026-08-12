package org.wallet.rogalik.mcp.server.exec;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * What actually happened when one command ran.
 *
 * <p>The distinction that matters for datapack work is between a command that failed to parse,
 * one that parsed but threw while executing, and one that ran to completion but reported no
 * success — a macro missing a {@code $(key)}, or a trigger that was never armed. All three used
 * to arrive as {@code status: "unknown"}.
 */
public record CommandOutcome(
    String command,
    boolean success,
    int result,
    String error,
    ErrorType errorType,
    Integer cursor,
    List<String> messages,
    long executionTimeMs
) {

    public enum ErrorType {
        /** The command could not be parsed; {@link #cursor} points at the offending character. */
        PARSE,
        /** The command parsed but threw while executing. */
        RUNTIME,
        /** The safety validator refused to run it. */
        SAFETY,
        /** Never submitted, because an earlier command in the batch failed. */
        SKIPPED,
        /**
         * Sent to a server this process does not host, so the outcome is genuinely unobservable.
         * This is the only case that still reports {@code status: "unknown"}.
         */
        REMOTE
    }

    public static CommandOutcome succeeded(String command, int result, List<String> messages, long elapsedMs) {
        return new CommandOutcome(command, true, result, null, null, null, messages, elapsedMs);
    }

    public static CommandOutcome ranWithoutSuccess(String command, int result, List<String> messages, long elapsedMs) {
        return new CommandOutcome(command, false, result, null, null, null, messages, elapsedMs);
    }

    public static CommandOutcome parseError(String command, String message, int cursor, long elapsedMs) {
        return new CommandOutcome(command, false, 0, message, ErrorType.PARSE, cursor, List.of(), elapsedMs);
    }

    public static CommandOutcome runtimeError(String command, String message, List<String> messages, long elapsedMs) {
        return new CommandOutcome(command, false, 0, message, ErrorType.RUNTIME, null, messages, elapsedMs);
    }

    public static CommandOutcome rejectedBySafety(String command, String reason) {
        return new CommandOutcome(command, false, 0, reason, ErrorType.SAFETY, null, List.of(), 0L);
    }

    public static CommandOutcome skipped(String command, String reason) {
        return new CommandOutcome(command, false, 0, reason, ErrorType.SKIPPED, null, List.of(), 0L);
    }

    public static CommandOutcome sentToRemote(String command, List<String> messages, long elapsedMs) {
        return new CommandOutcome(command, false, 0,
            "Sent to a remote server; a client cannot observe whether it succeeded. "
                + "Load the world in singleplayer, or run the mod on the server, for real status.",
            ErrorType.REMOTE, null, messages, elapsedMs);
    }

    /**
     * Legacy status string. Kept so existing clients keep working, but now derived from what the
     * command reported rather than guessed from chat text.
     */
    public String legacyStatus() {
        if (errorType == null) {
            return success ? "success" : "failed";
        }
        return switch (errorType) {
            case PARSE -> "rejected_by_game";
            case RUNTIME -> "execution_error";
            case SAFETY -> "rejected_by_safety";
            case SKIPPED -> "skipped";
            case REMOTE -> "unknown";
        };
    }

    /** True when the game accepted the command for execution at all. */
    public boolean accepted() {
        return errorType != ErrorType.PARSE
            && errorType != ErrorType.SAFETY
            && errorType != ErrorType.SKIPPED;
    }

    public String summary() {
        if (error != null) {
            return switch (errorType) {
                case PARSE -> "Syntax error at position " + cursor + ": " + error;
                case RUNTIME -> "Command failed while executing: " + error;
                case SAFETY -> "Rejected by safety validator: " + error;
                case SKIPPED -> error;
                case REMOTE -> error;
            };
        }
        if (success) {
            return "Command succeeded (result " + result + ")";
        }
        return "Command ran but reported no success. This is what a silently failing function or "
            + "an unarmed trigger looks like - it is not the same as a syntax error.";
    }

    public JsonObject toJson(int index) {
        JsonObject json = new JsonObject();
        json.addProperty("index", index);
        json.addProperty("command", command);

        json.addProperty("success", success);
        json.addProperty("result", result);
        if (error != null) {
            json.addProperty("error", error);
            json.addProperty("errorType", errorType.name().toLowerCase());
        } else {
            json.add("error", JsonNull.INSTANCE);
            json.add("errorType", JsonNull.INSTANCE);
        }
        if (cursor != null) {
            json.addProperty("cursor", cursor);
        }

        JsonArray messageArray = new JsonArray();
        for (String message : messages) {
            messageArray.add(message);
        }
        json.add("messages", messageArray);
        // Older clients read this key; keep both pointing at the same data.
        json.add("chatMessages", messageArray.deepCopy());

        json.addProperty("executionTimeMs", executionTimeMs);
        json.addProperty("status", legacyStatus());
        json.addProperty("accepted", accepted());
        json.addProperty("applied", success);
        json.addProperty("summary", summary());
        return json;
    }
}
