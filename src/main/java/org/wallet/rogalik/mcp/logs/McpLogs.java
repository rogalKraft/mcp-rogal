package org.wallet.rogalik.mcp.logs;

import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.logs.LogEntry.LogLevel;
import org.wallet.rogalik.mcp.logs.LogEntry.LogSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process-wide access to the captured log tail.
 *
 * <p>The buffer has to be reachable from a Log4j appender, from the chat mixin and from the MCP
 * tools, none of which can be handed a reference through a constructor, so it lives here.
 */
public final class McpLogs {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpLogs.class);

    private static volatile LogRingBuffer buffer;
    private static volatile boolean chatCaptureEnabled = true;

    private McpLogs() {
    }

    /** Wires the appender into the running logger context. Safe to call more than once. */
    public static synchronized void install(MCPConfig config) {
        if (buffer != null) {
            return;
        }
        if (!config.getLogs().isEnabled()) {
            LOGGER.info("Log capture disabled by config");
            return;
        }

        buffer = new LogRingBuffer(config.getLogs().getBufferSize());
        chatCaptureEnabled = config.getLogs().isCaptureChat();

        LogLevel minLevel = LogLevel.fromName(config.getLogs().getMinLevel());
        McpLogAppender.install(buffer, minLevel);
    }

    /** Null until {@link #install} has run; tools must handle that. */
    public static LogRingBuffer buffer() {
        return buffer;
    }

    public static boolean isReady() {
        return buffer != null;
    }

    /**
     * Records a chat or command-feedback line.
     *
     * <p>Chat never reaches the log file, but it is where the game reports most of what a
     * datapack does, so it belongs in the same searchable tail as everything else.
     */
    public static void recordChat(String message) {
        LogRingBuffer current = buffer;
        if (current == null || !chatCaptureEnabled || message == null || message.isBlank()) {
            return;
        }
        current.append(LogLevel.INFO, "chat", Thread.currentThread().getName(),
            LogSource.CHAT, message, null);
    }

    /** Test hook: replaces the buffer without touching the logging backend. */
    static synchronized void setBufferForTesting(LogRingBuffer replacement) {
        buffer = replacement;
    }
}
