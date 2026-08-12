package org.wallet.rogalik.mcp.logs;

import org.wallet.rogalik.mcp.logs.LogEntry.LogLevel;
import org.wallet.rogalik.mcp.logs.LogEntry.LogSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bounded in-memory log tail with cursor-based reads.
 *
 * <p>Every entry gets an id that never repeats, so a caller polls with the {@code nextId} from
 * its previous read and receives only what has arrived since. When the buffer overflows, the
 * number of evicted entries is reported rather than silently swallowed — a reader that fell
 * behind needs to know it has a gap.
 */
public class LogRingBuffer {

    private static final int DEFAULT_QUERY_LIMIT = 200;

    private final int capacity;
    private final ArrayDeque<LogEntry> entries;
    private final Object lock = new Object();

    private long nextId = 1;
    private long droppedTotal = 0;

    public LogRingBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.entries = new ArrayDeque<>(Math.min(this.capacity, 1024));
    }

    public LogEntry append(
        LogLevel level,
        String logger,
        String thread,
        LogSource source,
        String message,
        String throwable
    ) {
        LogEntry entry;
        synchronized (lock) {
            entry = new LogEntry(nextId++, System.currentTimeMillis(), level,
                logger == null ? "" : logger,
                thread == null ? "" : thread,
                source, message == null ? "" : message, throwable);

            if (entries.size() >= capacity) {
                entries.removeFirst();
                droppedTotal++;
            }
            entries.addLast(entry);

            // Wakes anything blocked in awaitMatch.
            lock.notifyAll();
        }
        return entry;
    }

    /** The id the next appended entry will receive; a fresh reader can start here to skip history. */
    public long nextId() {
        synchronized (lock) {
            return nextId;
        }
    }

    public long droppedTotal() {
        synchronized (lock) {
            return droppedTotal;
        }
    }

    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    public void clear() {
        synchronized (lock) {
            entries.clear();
            droppedTotal = 0;
        }
    }

    public Result query(Query query) {
        synchronized (lock) {
            List<LogEntry> matched = new ArrayList<>();
            long missed = 0;

            if (query.sinceId() > 0 && !entries.isEmpty()) {
                long oldestRetained = entries.peekFirst().id();
                if (query.sinceId() < oldestRetained) {
                    missed = oldestRetained - query.sinceId();
                }
            }

            for (LogEntry entry : entries) {
                if (entry.id() < query.sinceId()) {
                    continue;
                }
                if (query.matches(entry)) {
                    matched.add(entry);
                }
            }

            // tail takes the newest N of whatever matched, which is what a human "tail -n" means.
            int limit = query.limit() > 0 ? query.limit() : DEFAULT_QUERY_LIMIT;
            if (matched.size() > limit) {
                matched = new ArrayList<>(matched.subList(matched.size() - limit, matched.size()));
            }

            return new Result(matched, nextId, missed, droppedTotal);
        }
    }

    /**
     * Blocks until an entry matching {@code query} arrives, or the timeout elapses.
     *
     * <p>Entries already buffered at or after {@code sinceId} are checked first, so a match that
     * landed between the caller's last read and this call is not missed.
     *
     * @return the first matching entry, or null on timeout
     */
    public LogEntry awaitMatch(Query query, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;

        synchronized (lock) {
            long cursor = query.sinceId();
            while (true) {
                for (LogEntry entry : entries) {
                    if (entry.id() >= cursor && query.matches(entry)) {
                        return entry;
                    }
                }
                cursor = nextId;

                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                lock.wait(remaining);
            }
        }
    }

    /** Entries around a given id, for showing what led up to a match. */
    public List<LogEntry> contextAround(long id, int before, int after) {
        synchronized (lock) {
            List<LogEntry> all = new ArrayList<>(entries);
            int index = -1;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id() == id) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                return List.of();
            }
            int from = Math.max(0, index - before);
            int to = Math.min(all.size(), index + after + 1);
            return List.copyOf(all.subList(from, to));
        }
    }

    /** Filter criteria shared by {@link #query} and {@link #awaitMatch}. */
    public record Query(
        long sinceId,
        LogLevel minLevel,
        Pattern loggerPattern,
        Pattern messagePattern,
        Set<LogSource> sources,
        int limit
    ) {

        public static Query all() {
            return new Query(0, LogLevel.TRACE, null, null, EnumSet.allOf(LogSource.class), DEFAULT_QUERY_LIMIT);
        }

        public boolean matches(LogEntry entry) {
            if (!entry.level().isAtLeast(minLevel)) {
                return false;
            }
            if (sources != null && !sources.isEmpty() && !sources.contains(entry.source())) {
                return false;
            }
            if (loggerPattern != null && !loggerPattern.matcher(entry.logger()).find()) {
                return false;
            }
            if (messagePattern == null) {
                return true;
            }
            if (messagePattern.matcher(entry.message()).find()) {
                return true;
            }
            // A stack trace is part of the message as far as searching goes.
            return entry.throwable() != null && messagePattern.matcher(entry.throwable()).find();
        }
    }

    public record Result(List<LogEntry> entries, long nextId, long missed, long droppedTotal) {
    }
}
