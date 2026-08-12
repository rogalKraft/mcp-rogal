package org.wallet.rogalik.mcp.logs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.logs.LogEntry.LogLevel;
import org.wallet.rogalik.mcp.logs.LogEntry.LogSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LogQueryParserTest {

    @Test
    public void emptyArgumentsMatchEverything() {
        LogRingBuffer.Query query = LogQueryParser.parse(new JsonObject(), 200);

        assertEquals(0, query.sinceId());
        assertEquals(LogLevel.TRACE, query.minLevel());
        assertNull(query.messagePattern());
        assertNull(query.loggerPattern());
        assertEquals(200, query.limit());
        assertTrue(query.sources().containsAll(java.util.List.of(LogSource.GAME, LogSource.CHAT)));
    }

    @Test
    public void tailOverridesLimit() {
        JsonObject args = new JsonObject();
        args.addProperty("limit", 500);
        args.addProperty("tail", 25);

        assertEquals(25, LogQueryParser.parse(args, 200).limit());
    }

    @Test
    public void limitIsClampedToASaneRange() {
        JsonObject tooBig = new JsonObject();
        tooBig.addProperty("limit", 999999);
        assertEquals(2000, LogQueryParser.parse(tooBig, 200).limit());

        JsonObject tooSmall = new JsonObject();
        tooSmall.addProperty("limit", 0);
        assertEquals(1, LogQueryParser.parse(tooSmall, 200).limit());
    }

    @Test
    public void negativeSinceIdIsTreatedAsFromTheStart() {
        JsonObject args = new JsonObject();
        args.addProperty("since_id", -5);

        assertEquals(0, LogQueryParser.parse(args, 200).sinceId());
    }

    @Test
    public void patternsAreCompiledCaseInsensitively() {
        JsonObject args = new JsonObject();
        args.addProperty("pattern", "failed to load");

        assertTrue(LogQueryParser.parse(args, 200).messagePattern().matcher("FAILED TO LOAD pack").find());
    }

    @Test
    public void messagePatternIsAcceptedAsAnAliasForPattern() {
        JsonObject args = new JsonObject();
        args.addProperty("message_pattern", "boom");

        assertNotNull(LogQueryParser.parse(args, 200).messagePattern());
    }

    @Test
    public void invalidRegexIsReportedRatherThanThrownRaw() {
        JsonObject args = new JsonObject();
        args.addProperty("pattern", "unclosed [group");

        LogQueryParser.InvalidQueryException e = assertThrows(
            LogQueryParser.InvalidQueryException.class, () -> LogQueryParser.parse(args, 200));
        assertTrue(e.getMessage().contains("Invalid regular expression"), e.getMessage());
    }

    @Test
    public void sourcesRestrictTheQuery() {
        JsonArray sources = new JsonArray();
        sources.add("chat");
        JsonObject args = new JsonObject();
        args.add("sources", sources);

        LogRingBuffer.Query query = LogQueryParser.parse(args, 200);

        assertEquals(java.util.Set.of(LogSource.CHAT), query.sources());
    }

    @Test
    public void unknownSourceIsRejectedWithAHelpfulMessage() {
        JsonArray sources = new JsonArray();
        sources.add("telepathy");
        JsonObject args = new JsonObject();
        args.add("sources", sources);

        LogQueryParser.InvalidQueryException e = assertThrows(
            LogQueryParser.InvalidQueryException.class, () -> LogQueryParser.parse(args, 200));
        assertTrue(e.getMessage().contains("game, chat"), e.getMessage());
    }

    @Test
    public void emptyPatternIsTreatedAsNoFilter() {
        JsonObject args = new JsonObject();
        args.addProperty("pattern", "");

        assertNull(LogQueryParser.parse(args, 200).messagePattern());
    }
}
