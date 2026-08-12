package org.wallet.rogalik.mcp.logs;

import org.wallet.rogalik.mcp.logs.LogEntry.LogLevel;
import org.wallet.rogalik.mcp.logs.LogEntry.LogSource;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class LogRingBufferTest {

    private static LogRingBuffer.Query query(long sinceId, LogLevel minLevel, String pattern, int limit) {
        return new LogRingBuffer.Query(
            sinceId,
            minLevel,
            null,
            pattern == null ? null : Pattern.compile(pattern, Pattern.CASE_INSENSITIVE),
            EnumSet.allOf(LogSource.class),
            limit);
    }

    private static void add(LogRingBuffer buffer, LogLevel level, String message) {
        buffer.append(level, "test", "main", LogSource.GAME, message, null);
    }

    @Test
    public void idsAreMonotonicAndStartAtOne() {
        LogRingBuffer buffer = new LogRingBuffer(10);

        assertEquals(1, buffer.append(LogLevel.INFO, "a", "t", LogSource.GAME, "first", null).id());
        assertEquals(2, buffer.append(LogLevel.INFO, "a", "t", LogSource.GAME, "second", null).id());
        assertEquals(3, buffer.nextId());
    }

    @Test
    public void cursorReadReturnsOnlyWhatIsNew() {
        LogRingBuffer buffer = new LogRingBuffer(10);
        add(buffer, LogLevel.INFO, "old");

        LogRingBuffer.Result first = buffer.query(query(0, LogLevel.TRACE, null, 100));
        assertEquals(1, first.entries().size());

        add(buffer, LogLevel.INFO, "new");

        LogRingBuffer.Result second = buffer.query(query(first.nextId(), LogLevel.TRACE, null, 100));
        assertEquals(1, second.entries().size());
        assertEquals("new", second.entries().get(0).message());
    }

    @Test
    public void overflowEvictsOldestAndCountsTheLoss() {
        LogRingBuffer buffer = new LogRingBuffer(3);
        for (int i = 1; i <= 5; i++) {
            add(buffer, LogLevel.INFO, "line" + i);
        }

        assertEquals(3, buffer.size());
        assertEquals(2, buffer.droppedTotal());

        LogRingBuffer.Result result = buffer.query(query(0, LogLevel.TRACE, null, 100));
        assertEquals(List.of("line3", "line4", "line5"),
            result.entries().stream().map(LogEntry::message).toList());
    }

    @Test
    public void readingFromACursorOlderThanTheBufferReportsTheGap() {
        LogRingBuffer buffer = new LogRingBuffer(3);
        for (int i = 1; i <= 6; i++) {
            add(buffer, LogLevel.INFO, "line" + i);
        }

        // Entries 1-3 are gone; a reader still holding cursor 1 has missed three.
        LogRingBuffer.Result result = buffer.query(query(1, LogLevel.TRACE, null, 100));

        assertEquals(3, result.missed());
        assertEquals(3, result.droppedTotal());
    }

    @Test
    public void upToDateCursorReportsNoGap() {
        LogRingBuffer buffer = new LogRingBuffer(3);
        add(buffer, LogLevel.INFO, "only");

        LogRingBuffer.Result result = buffer.query(query(1, LogLevel.TRACE, null, 100));

        assertEquals(0, result.missed());
    }

    @Test
    public void minimumLevelFiltersLowerSeverities() {
        LogRingBuffer buffer = new LogRingBuffer(10);
        add(buffer, LogLevel.DEBUG, "noise");
        add(buffer, LogLevel.INFO, "normal");
        add(buffer, LogLevel.ERROR, "boom");

        LogRingBuffer.Result result = buffer.query(query(0, LogLevel.WARN, null, 100));

        assertEquals(1, result.entries().size());
        assertEquals("boom", result.entries().get(0).message());
    }

    @Test
    public void patternMatchingIsCaseInsensitiveAndSearchesStackTraces() {
        LogRingBuffer buffer = new LogRingBuffer(10);
        add(buffer, LogLevel.INFO, "Loading datapack");
        buffer.append(LogLevel.ERROR, "test", "main", LogSource.GAME, "handler blew up",
            "java.lang.IllegalStateException: unknown loot table");

        assertEquals(1, buffer.query(query(0, LogLevel.TRACE, "DATAPACK", 100)).entries().size());

        LogRingBuffer.Result inTrace = buffer.query(query(0, LogLevel.TRACE, "unknown loot table", 100));
        assertEquals(1, inTrace.entries().size(), "A pattern should match text inside the stack trace");
    }

    @Test
    public void sourceFilterSeparatesChatFromGameLog() {
        LogRingBuffer buffer = new LogRingBuffer(10);
        add(buffer, LogLevel.INFO, "from the log");
        buffer.append(LogLevel.INFO, "chat", "main", LogSource.CHAT, "Set the time to 1000", null);

        LogRingBuffer.Query chatOnly = new LogRingBuffer.Query(
            0, LogLevel.TRACE, null, null, EnumSet.of(LogSource.CHAT), 100);

        List<LogEntry> entries = buffer.query(chatOnly).entries();
        assertEquals(1, entries.size());
        assertEquals("Set the time to 1000", entries.get(0).message());
    }

    @Test
    public void limitKeepsTheNewestMatches() {
        LogRingBuffer buffer = new LogRingBuffer(100);
        for (int i = 1; i <= 10; i++) {
            add(buffer, LogLevel.INFO, "line" + i);
        }

        List<LogEntry> entries = buffer.query(query(0, LogLevel.TRACE, null, 3)).entries();

        assertEquals(3, entries.size());
        assertEquals("line8", entries.get(0).message());
        assertEquals("line10", entries.get(2).message());
    }

    @Test
    public void awaitMatchReturnsImmediatelyForAnEntryAlreadyBuffered() throws Exception {
        LogRingBuffer buffer = new LogRingBuffer(10);
        add(buffer, LogLevel.ERROR, "Failed to load datapack");

        LogEntry match = buffer.awaitMatch(query(0, LogLevel.TRACE, "Failed to load", 1), 50);

        assertNotNull(match, "A line that arrived before the wait began must still be found");
        assertEquals("Failed to load datapack", match.message());
    }

    @Test
    public void awaitMatchWakesWhenAMatchingEntryArrives() throws Exception {
        LogRingBuffer buffer = new LogRingBuffer(10);

        Thread producer = new Thread(() -> {
            try {
                Thread.sleep(50);
                add(buffer, LogLevel.ERROR, "Couldn't load recipe");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();

        LogEntry match = buffer.awaitMatch(query(0, LogLevel.TRACE, "couldn't load", 1), 5000);
        producer.join();

        assertNotNull(match);
        assertEquals("Couldn't load recipe", match.message());
    }

    @Test
    public void awaitMatchReturnsNullOnTimeout() throws Exception {
        LogRingBuffer buffer = new LogRingBuffer(10);
        add(buffer, LogLevel.INFO, "nothing interesting");

        long startedAt = System.currentTimeMillis();
        LogEntry match = buffer.awaitMatch(query(0, LogLevel.TRACE, "never appears", 1), 120);

        assertNull(match);
        assertTrue(System.currentTimeMillis() - startedAt >= 100, "Should have waited out the timeout");
    }

    @Test
    public void awaitMatchIgnoresEntriesBeforeTheCursor() throws Exception {
        LogRingBuffer buffer = new LogRingBuffer(10);
        add(buffer, LogLevel.ERROR, "old failure");
        long cursor = buffer.nextId();

        LogEntry match = buffer.awaitMatch(query(cursor, LogLevel.TRACE, "old failure", 1), 100);

        assertNull(match, "A match from before the cursor must not satisfy the wait");
    }

    @Test
    public void contextAroundReturnsNeighbouringEntries() {
        LogRingBuffer buffer = new LogRingBuffer(20);
        for (int i = 1; i <= 10; i++) {
            add(buffer, LogLevel.INFO, "line" + i);
        }

        List<LogEntry> context = buffer.contextAround(5, 2, 1);

        assertEquals(List.of("line3", "line4", "line5", "line6"),
            context.stream().map(LogEntry::message).toList());
    }

    @Test
    public void contextAroundAnUnknownIdIsEmpty() {
        LogRingBuffer buffer = new LogRingBuffer(10);
        add(buffer, LogLevel.INFO, "only");

        assertTrue(buffer.contextAround(999, 2, 2).isEmpty());
    }

    @Test
    public void levelNamesMapOntoTheLocalScale() {
        assertEquals(LogLevel.WARN, LogLevel.fromName("warning"));
        assertEquals(LogLevel.ERROR, LogLevel.fromName("SEVERE"));
        assertEquals(LogLevel.TRACE, LogLevel.fromName("ALL"));
        assertEquals(LogLevel.INFO, LogLevel.fromName("nonsense"), "Unknown levels default to INFO");
        assertEquals(LogLevel.INFO, LogLevel.fromName(null));
    }
}
