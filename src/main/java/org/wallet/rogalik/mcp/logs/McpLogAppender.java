package org.wallet.rogalik.mcp.logs;

import org.wallet.rogalik.mcp.logs.LogEntry.LogLevel;
import org.wallet.rogalik.mcp.logs.LogEntry.LogSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Copies everything the game logs into an in-memory ring buffer.
 *
 * <p>Attached programmatically rather than through {@code log4j2.xml}: Minecraft supplies its own
 * Log4j configuration at startup, and the copy bundled in this mod's resources disables the root
 * logger outright, so a file-based appender definition would never take effect.
 */
public class McpLogAppender extends AbstractAppender {

    private static final String APPENDER_NAME = "McpRingBuffer";

    /**
     * Guards against recursion: anything logged while handling an event — including a failure
     * inside this appender — would otherwise re-enter and never terminate.
     */
    private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final LogRingBuffer buffer;
    private final LogLevel minLevel;

    private McpLogAppender(LogRingBuffer buffer, LogLevel minLevel) {
        super(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY);
        this.buffer = buffer;
        this.minLevel = minLevel;
    }

    /**
     * Adds the appender to the root logger of the running context.
     *
     * <p>Failure here is not fatal — the mod keeps working, only the log tools go dark — so a
     * missing or unexpected logging backend is reported and swallowed.
     */
    static void install(LogRingBuffer buffer, LogLevel minLevel) {
        try {
            if (!(LogManager.getContext(false) instanceof LoggerContext context)) {
                LogManager.getLogger(McpLogAppender.class).warn(
                    "Log capture unavailable: the active Log4j context is not a core LoggerContext");
                return;
            }

            Configuration configuration = context.getConfiguration();
            if (configuration.getAppender(APPENDER_NAME) != null) {
                return;
            }

            McpLogAppender appender = new McpLogAppender(buffer, minLevel);
            appender.start();
            configuration.addAppender(appender);
            configuration.getRootLogger().addAppender(appender, null, null);
            context.updateLoggers();

            LogManager.getLogger(McpLogAppender.class)
                .info("MCP log capture attached (min level {})", minLevel);
        } catch (Throwable t) {
            // Deliberately broad: a logging-backend mismatch must not stop the mod from loading.
            LogManager.getLogger(McpLogAppender.class).warn("Failed to attach MCP log capture", t);
        }
    }

    /** Detaches the appender; used when the mod shuts down. */
    public static void uninstall() {
        try {
            if (LogManager.getContext(false) instanceof LoggerContext context) {
                Configuration configuration = context.getConfiguration();
                Appender existing = configuration.getAppender(APPENDER_NAME);
                if (existing != null) {
                    configuration.getRootLogger().removeAppender(APPENDER_NAME);
                    existing.stop();
                    context.updateLoggers();
                }
            }
        } catch (Throwable ignored) {
            // Shutting down; nothing useful to do with a failure here.
        }
    }

    @Override
    public void append(LogEvent event) {
        if (Boolean.TRUE.equals(REENTRANT.get())) {
            return;
        }

        LogLevel level = LogLevel.fromName(event.getLevel().name());
        if (!level.isAtLeast(minLevel)) {
            return;
        }

        REENTRANT.set(Boolean.TRUE);
        try {
            buffer.append(
                level,
                event.getLoggerName(),
                event.getThreadName(),
                LogSource.GAME,
                event.getMessage() == null ? "" : event.getMessage().getFormattedMessage(),
                stackTraceOf(event.getThrown()));
        } catch (Throwable ignored) {
            // A failure to capture a log line must never break the line's real destination.
        } finally {
            REENTRANT.set(Boolean.FALSE);
        }
    }

    private static String stackTraceOf(Throwable thrown) {
        if (thrown == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        thrown.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
