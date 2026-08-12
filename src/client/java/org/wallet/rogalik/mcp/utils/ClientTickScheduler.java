package org.wallet.rogalik.mcp.utils;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs work on the client thread a fixed number of ticks from now.
 *
 * <p>Needed wherever an action only becomes observable on a later frame: releasing a held key,
 * letting the world render after the camera moved, or waiting out a tool's requested delay.
 */
public final class ClientTickScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientTickScheduler.class);

    private static final List<ScheduledTask> pending = new ArrayList<>();
    private static final AtomicLong tickCounter = new AtomicLong();

    private ClientTickScheduler() {
    }

    private static final class ScheduledTask {
        private final Runnable runnable;
        private int remainingTicks;

        private ScheduledTask(Runnable runnable, int remainingTicks) {
            this.runnable = runnable;
            this.remainingTicks = remainingTicks;
        }
    }

    /**
     * Schedules {@code runnable} to run on the client thread after {@code ticks} end-of-tick
     * events. A non-positive count runs it on the very next tick rather than immediately, so
     * callers always get consistent off-the-caller-thread behaviour.
     */
    public static void runAfterTicks(int ticks, Runnable runnable) {
        synchronized (pending) {
            pending.add(new ScheduledTask(runnable, Math.max(1, ticks)));
        }
    }

    /** Completes once {@code ticks} client ticks have elapsed. */
    public static CompletableFuture<Void> waitTicks(int ticks) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runAfterTicks(ticks, () -> future.complete(null));
        return future;
    }

    /** Monotonic count of client ticks observed since the game started. */
    public static long currentTick() {
        return tickCounter.get();
    }

    /**
     * Drives the queue; register against {@code ClientTickEvents.END_CLIENT_TICK}.
     *
     * <p>Due tasks are collected under the lock but invoked outside it, so a task that schedules
     * further work does not mutate the list mid-iteration.
     */
    public static void onEndTick(Minecraft client) {
        tickCounter.incrementAndGet();

        List<Runnable> due = null;
        synchronized (pending) {
            Iterator<ScheduledTask> it = pending.iterator();
            while (it.hasNext()) {
                ScheduledTask task = it.next();
                if (--task.remainingTicks <= 0) {
                    if (due == null) {
                        due = new ArrayList<>();
                    }
                    due.add(task.runnable);
                    it.remove();
                }
            }
        }

        if (due == null) {
            return;
        }
        for (Runnable runnable : due) {
            try {
                runnable.run();
            } catch (Exception e) {
                LOGGER.error("Scheduled client task failed", e);
            }
        }
    }

    /** Drops every queued task; used by tests and on disconnect. */
    public static void clear() {
        synchronized (pending) {
            pending.clear();
        }
    }

    static int pendingCount() {
        synchronized (pending) {
            return pending.size();
        }
    }
}
