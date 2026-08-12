package org.wallet.rogalik.mcp.input;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.tools.McpTool;
import org.wallet.rogalik.mcp.server.tools.SchemaBuilder;
import org.wallet.rogalik.mcp.utils.ClientTickScheduler;
import net.minecraft.client.KeyMapping;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SendKeybindTool implements McpTool {

    private final MCPConfig config;

    public SendKeybindTool(MCPConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "send_keybind";
    }

    @Override
    public String description() {
        return "Trigger a key binding by its id, whatever key it happens to be bound to.\n\n"
            + "Prefer this over press_key for game actions: press_key('e') breaks if the player "
            + "rebound the inventory, while send_keybind('key.inventory') does not.\n\n"
            + "Common ids: key.inventory, key.jump, key.sneak, key.sprint, key.attack, key.use, "
            + "key.drop, key.chat, key.command, key.forward, key.back, key.left, key.right, "
            + "key.playerlist, key.screenshot. Run list_keybinds for the full set.\n\n"
            + "hold_ticks holds the binding down - that is how you walk (key.forward), sneak or "
            + "keep an attack going. 20 ticks is roughly a second.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .string("keybind_id", "Binding id, e.g. 'key.inventory' or just 'inventory'", true)
            .integer("hold_ticks", "Client ticks to hold the binding before releasing", false, 0)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        if (!config.getInput().isEnabled()) {
            return MCPProtocol.createErrorResponse(
                "Simulated input is disabled. Set input.enabled to true in mcp.json.", null);
        }
        if (!arguments.has("keybind_id")) {
            return MCPProtocol.createErrorResponse("Missing required parameter: keybind_id", null);
        }

        String id = arguments.get("keybind_id").getAsString();
        Optional<KeyMapping> mapping = KeybindLookup.byName(id);
        if (mapping.isEmpty()) {
            return MCPProtocol.createErrorResponse(
                "No key binding called '" + id + "'. Use list_keybinds to see what exists.", null);
        }

        KeyMapping keyMapping = mapping.get();
        Optional<InputConstants.Key> bound = KeybindLookup.boundKey(keyMapping);
        if (bound.isEmpty()) {
            return MCPProtocol.createErrorResponse(
                "Key binding '" + keyMapping.getName() + "' is unbound, so there is no input to send. "
                    + "Bind it in the controls menu first.", null);
        }

        InputConstants.Key key = bound.get();
        int holdTicks = arguments.has("hold_ticks")
            ? Math.clamp(arguments.get("hold_ticks").getAsInt(), 0, config.getInput().getMaxHoldTicks())
            : 0;

        JsonObject result = new JsonObject();
        result.addProperty("keybind", keyMapping.getName());
        result.addProperty("boundTo", key.getName());
        result.addProperty("heldTicks", holdTicks);

        try {
            if (key.getType() == InputConstants.Type.MOUSE) {
                dispatchMouse(key.getValue(), holdTicks);
                result.addProperty("inputType", "mouse");
            } else {
                dispatchKey(key.getValue(), holdTicks);
                result.addProperty("inputType", "keyboard");
            }
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse(
                "Failed to send key binding '" + keyMapping.getName() + "': " + e.getMessage(), null);
        }

        return MCPProtocol.createSuccessResponse(result.toString());
    }

    private void dispatchKey(int keyCode, int holdTicks) throws Exception {
        if (holdTicks <= 0) {
            await(InputDispatcher.onClientThread(
                () -> InputDispatcher.tapKeyWithModifiers(keyCode, List.of(), 0)));
            return;
        }

        await(InputDispatcher.onClientThread(() -> InputDispatcher.pressKey(keyCode, 0)));
        await(scheduleRelease(() -> InputDispatcher.releaseKey(keyCode, 0), holdTicks));
    }

    private void dispatchMouse(int button, int holdTicks) throws Exception {
        if (holdTicks <= 0) {
            await(InputDispatcher.onClientThread(() -> InputDispatcher.clickMouse(button, 0)));
            return;
        }

        await(InputDispatcher.onClientThread(
            () -> InputDispatcher.mouseButton(button, 0, org.lwjgl.glfw.GLFW.GLFW_PRESS)));
        await(scheduleRelease(
            () -> InputDispatcher.mouseButton(button, 0, org.lwjgl.glfw.GLFW.GLFW_RELEASE), holdTicks));
    }

    private CompletableFuture<Void> scheduleRelease(Runnable release, int holdTicks) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ClientTickScheduler.runAfterTicks(holdTicks, () -> {
            try {
                release.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private void await(CompletableFuture<Void> future) throws Exception {
        future.get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
    }
}
