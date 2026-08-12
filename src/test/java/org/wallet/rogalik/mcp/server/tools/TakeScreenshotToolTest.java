package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TakeScreenshotToolTest {

    @Test
    public void successProducesAnImageContentBlock() {
        StubTool tool = new StubTool(CompletableFuture.completedFuture("abc123"));

        JsonObject response = tool.call(new JsonObject());

        assertFalse(response.get("isError").getAsBoolean());
        JsonObject firstContent = response.getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals("image", firstContent.get("type").getAsString());
        assertEquals("abc123", firstContent.get("data").getAsString());
        assertEquals("image/png", firstContent.get("mimeType").getAsString());
    }

    @Test
    public void timeoutCancelsTheCaptureAndReports() {
        TimeoutFuture future = new TimeoutFuture();
        StubTool tool = new StubTool(future);

        JsonObject response = tool.call(new JsonObject());

        assertTrue(response.get("isError").getAsBoolean());
        assertTrue(extractText(response).contains("timed out"));
        assertTrue(future.cancelCalled, "Future should be cancelled on timeout");
    }

    @Test
    public void interruptionCancelsTheCaptureAndPreservesTheFlag() {
        InterruptedFuture future = new InterruptedFuture();
        StubTool tool = new StubTool(future);

        try {
            JsonObject response = tool.call(new JsonObject());

            assertTrue(response.get("isError").getAsBoolean());
            assertTrue(extractText(response).contains("interrupted"));
            assertTrue(future.cancelCalled, "Future should be cancelled on interruption");
            assertTrue(Thread.currentThread().isInterrupted(), "Interrupted flag should be preserved");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void captureFailureSurfacesTheUnderlyingCause() {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("capture failed"));
        StubTool tool = new StubTool(future);

        JsonObject response = tool.call(new JsonObject());

        assertTrue(response.get("isError").getAsBoolean());
        assertTrue(extractText(response).contains("capture failed"));
    }

    @Test
    public void nullArgumentsAreTreatedAsAnEmptyRequest() {
        StubTool tool = new StubTool(CompletableFuture.completedFuture("no-args-image"));

        JsonObject response = tool.call(null);

        assertFalse(response.get("isError").getAsBoolean());
        assertNotNull(tool.seenParams.get(), "Params should be normalised, not passed through as null");
        assertTrue(tool.seenParams.get().isEmpty());
    }

    private static String extractText(JsonObject response) {
        JsonArray content = response.getAsJsonArray("content");
        return content.get(0).getAsJsonObject().get("text").getAsString();
    }

    /** Supplies a canned future instead of touching the render thread. */
    private static class StubTool extends TakeScreenshotTool {
        private final CompletableFuture<String> future;
        private final AtomicReference<JsonObject> seenParams = new AtomicReference<>();

        StubTool(CompletableFuture<String> future) {
            super(null, new MCPConfig());
            this.future = future;
        }

        @Override
        CompletableFuture<String> takeScreenshotAsync(JsonObject params) {
            seenParams.set(params);
            return future;
        }
    }

    private static class TimeoutFuture extends CompletableFuture<String> {
        boolean cancelCalled = false;

        @Override
        public String get(long timeout, TimeUnit unit) throws TimeoutException {
            throw new TimeoutException("forced timeout");
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelCalled = true;
            return super.cancel(mayInterruptIfRunning);
        }
    }

    private static class InterruptedFuture extends CompletableFuture<String> {
        boolean cancelCalled = false;

        @Override
        public String get(long timeout, TimeUnit unit) throws InterruptedException {
            throw new InterruptedException("forced interrupt");
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelCalled = true;
            return super.cancel(mayInterruptIfRunning);
        }
    }
}
