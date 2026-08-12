package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.utils.IScreenshotUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Client-only tool: captures the framebuffer, optionally after moving the player.
 *
 * <p>Registered only by the client entry point, so on a dedicated server it never appears in
 * {@code tools/list}.
 */
public class TakeScreenshotTool implements McpTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(TakeScreenshotTool.class);

    private final IScreenshotUtils screenshotUtils;
    private final MCPConfig config;

    public TakeScreenshotTool(IScreenshotUtils screenshotUtils, MCPConfig config) {
        this.screenshotUtils = screenshotUtils;
        this.config = config;
    }

    @Override
    public String name() {
        return "take_screenshot";
    }

    @Override
    public String description() {
        return "Capture a screenshot of the current Minecraft game screen, to visually inspect the "
            + "world, a build, or the player's surroundings.\n\n"
            + "Optionally moves the player first: if x, y and z are provided the player WILL be "
            + "teleported there, and if yaw or pitch are provided the camera direction WILL change.\n\n"
            + "For inspecting a menu or checking on-screen text, prefer get_open_screen - it returns "
            + "the rendered widget text directly, which is both cheaper and easier to assert on.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .number("x", "Optional X coordinate to teleport the player to", false)
            .number("y", "Optional Y coordinate to teleport the player to", false)
            .number("z", "Optional Z coordinate to teleport the player to", false)
            .number("yaw", "Optional horizontal view direction (-180 to 180)", false)
            .number("pitch", "Optional vertical view direction (-90 looking up to 90 looking down)", false)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        CompletableFuture<String> future;
        try {
            future = takeScreenshotAsync(arguments != null ? arguments : new JsonObject());
        } catch (Exception e) {
            LOGGER.error("Unexpected error taking screenshot", e);
            return MCPProtocol.createErrorResponse("Failed to take screenshot: " + e.getMessage(), null);
        }
        return awaitScreenshotResult(future);
    }

    /** Overridable so tests can supply a future without a render thread. */
    CompletableFuture<String> takeScreenshotAsync(JsonObject params) {
        return screenshotUtils.takeScreenshot(params);
    }

    JsonObject awaitScreenshotResult(CompletableFuture<String> future) {
        long timeoutMs = config.getServer().getRequestTimeoutMs();
        try {
            // Runs on an executor thread, not the render thread, so blocking here is safe.
            String base64Data = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return MCPProtocol.createImageResponse(base64Data, "image/png");
        } catch (TimeoutException e) {
            future.cancel(true);
            LOGGER.warn("Screenshot capture timed out after {} ms", timeoutMs);
            return MCPProtocol.createErrorResponse(
                "Screenshot capture timed out after " + timeoutMs + " ms", null);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            LOGGER.warn("Screenshot capture interrupted");
            return MCPProtocol.createErrorResponse("Screenshot capture was interrupted", null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            LOGGER.error("Error taking screenshot", cause);
            return MCPProtocol.createErrorResponse("Failed to take screenshot: " + cause.getMessage(), null);
        } catch (Exception e) {
            LOGGER.error("Unexpected error taking screenshot", e);
            return MCPProtocol.createErrorResponse("Failed to take screenshot: " + e.getMessage(), null);
        }
    }
}
