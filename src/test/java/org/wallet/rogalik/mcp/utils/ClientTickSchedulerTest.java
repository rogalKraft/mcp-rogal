package org.wallet.rogalik.mcp.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class ClientTickSchedulerTest {

    @BeforeEach
    public void setUp() {
        ClientTickScheduler.clear();
    }

    @Test
    public void taskRunsOnlyAfterRequestedTickCount() {
        AtomicBoolean ran = new AtomicBoolean(false);
        ClientTickScheduler.runAfterTicks(2, () -> ran.set(true));

        ClientTickScheduler.onEndTick(null);
        assertFalse(ran.get(), "Task should not run after only 1 tick");
        assertEquals(1, ClientTickScheduler.pendingCount());

        ClientTickScheduler.onEndTick(null);
        assertTrue(ran.get(), "Task should run after 2 ticks");
        assertEquals(0, ClientTickScheduler.pendingCount());
    }

    @Test
    public void tasksWithDifferentDelaysFireIndependently() {
        AtomicBoolean first = new AtomicBoolean(false);
        AtomicBoolean second = new AtomicBoolean(false);

        ClientTickScheduler.runAfterTicks(1, () -> first.set(true));
        ClientTickScheduler.runAfterTicks(3, () -> second.set(true));

        ClientTickScheduler.onEndTick(null);
        assertTrue(first.get());
        assertFalse(second.get());
        assertEquals(1, ClientTickScheduler.pendingCount());

        ClientTickScheduler.onEndTick(null);
        assertFalse(second.get());

        ClientTickScheduler.onEndTick(null);
        assertTrue(second.get());
        assertEquals(0, ClientTickScheduler.pendingCount());
    }

    @Test
    public void nonPositiveDelayRunsOnNextTickRatherThanImmediately() {
        AtomicBoolean ran = new AtomicBoolean(false);
        ClientTickScheduler.runAfterTicks(0, () -> ran.set(true));

        assertFalse(ran.get(), "Scheduling alone must not run the task");
        ClientTickScheduler.onEndTick(null);
        assertTrue(ran.get());
    }

    @Test
    public void taskSchedulingAnotherTaskDoesNotBreakIteration() {
        // The previous implementation invoked runnables while iterating the list under its own
        // lock, so a task that scheduled follow-up work blew up with ConcurrentModificationException.
        List<String> order = new ArrayList<>();
        ClientTickScheduler.runAfterTicks(1, () -> {
            order.add("first");
            ClientTickScheduler.runAfterTicks(1, () -> order.add("second"));
        });

        assertDoesNotThrow(() -> ClientTickScheduler.onEndTick(null));
        assertEquals(List.of("first"), order);

        ClientTickScheduler.onEndTick(null);
        assertEquals(List.of("first", "second"), order);
    }

    @Test
    public void waitTicksCompletesOnceDelayElapses() {
        CompletableFuture<Void> future = ClientTickScheduler.waitTicks(2);

        ClientTickScheduler.onEndTick(null);
        assertFalse(future.isDone());

        ClientTickScheduler.onEndTick(null);
        assertTrue(future.isDone());
    }

    @Test
    public void failingTaskDoesNotStopOtherTasks() {
        AtomicBoolean survivor = new AtomicBoolean(false);
        ClientTickScheduler.runAfterTicks(1, () -> {
            throw new IllegalStateException("boom");
        });
        ClientTickScheduler.runAfterTicks(1, () -> survivor.set(true));

        assertDoesNotThrow(() -> ClientTickScheduler.onEndTick(null));
        assertTrue(survivor.get());
        assertEquals(0, ClientTickScheduler.pendingCount());
    }

    @Test
    public void tickCounterAdvances() {
        long before = ClientTickScheduler.currentTick();
        ClientTickScheduler.onEndTick(null);
        ClientTickScheduler.onEndTick(null);
        assertEquals(before + 2, ClientTickScheduler.currentTick());
    }
}
