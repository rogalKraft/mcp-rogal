package org.wallet.rogalik.mcp.server.exec;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blocks a caller thread until the server has advanced a number of ticks.
 *
 * <p>State written by one command only becomes visible to a read on the following tick, so
 * without an explicit wait a read/write pair issued together silently observes stale values.
 */
public final class ServerTicks {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerTicks.class);
    private static final long POLL_INTERVAL_MS = 5L;

    private ServerTicks() {
    }

    /**
     * @return the number of ticks actually observed, which is lower than requested only if the
     *         wait timed out or the thread was interrupted
     */
    public static int await(MinecraftServer server, int ticks, long timeoutMs) {
        if (server == null || ticks <= 0) {
            return 0;
        }

        int startTick = server.getTickCount();
        int target = startTick + ticks;
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (server.getTickCount() < target) {
            if (System.currentTimeMillis() > deadline) {
                LOGGER.warn("Timed out after {} ms waiting for {} server ticks", timeoutMs, ticks);
                break;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return server.getTickCount() - startTick;
    }
}
