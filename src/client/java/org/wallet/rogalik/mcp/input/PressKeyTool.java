package org.wallet.rogalik.mcp.input;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.tools.McpTool;
import org.wallet.rogalik.mcp.server.tools.SchemaBuilder;
import org.wallet.rogalik.mcp.utils.ClientTickScheduler;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PressKeyTool implements McpTool {

    private final MCPConfig config;

    public PressKeyTool(MCPConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "press_key";
    }

    @Override
    public String description() {
        return "Press a key, optionally with modifiers held, exactly as the window would deliver it.\n\n"
            + "Works everywhere real input works: in the world, in menus and in text fields. "
            + "Use type_text for entering characters - a key event alone produces no text.\n\n"
            + "action:\n"
            + "- tap (default): press and release\n"
            + "- press: press and leave held; pair with a later release, or set hold_ticks\n"
            + "- release: release a key left held\n\n"
            + "hold_ticks holds the key for that many client ticks before releasing (20 ticks is "
            + "about a second) - this is how you walk, sneak or hold an attack.\n\n"
            + "Key names: single letters and digits, escape, enter, tab, space, backspace, delete, "
            + "arrow keys, f1-f25, numpad0-9, lshift/lctrl/lalt, and Minecraft translation keys "
            + "such as key.keyboard.left.shift.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .string("key", "Key to press, e.g. 'escape', 'e', 'f3', 'space'", true)
            .arrayOf("modifiers", "string", "Modifiers to hold: ctrl, shift, alt, super", false)
            .enumString("action", "What to do with the key", false, "tap", "press", "release")
            .integer("hold_ticks", "Client ticks to hold the key before releasing", false, 0)
            .integer("repeat", "How many times to repeat a tap", false, 1)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        if (!config.getInput().isEnabled()) {
            return MCPProtocol.createErrorResponse(
                "Simulated input is disabled. Set input.enabled to true in mcp.json.", null);
        }
        if (!arguments.has("key")) {
            return MCPProtocol.createErrorResponse("Missing required parameter: key", null);
        }

        String keyName = arguments.get("key").getAsString();
        OptionalInt keyCode = KeyCodes.keyByName(keyName);
        if (keyCode.isEmpty()) {
            return MCPProtocol.createErrorResponse(
                "Unknown key '" + keyName + "'. Examples: escape, enter, space, a, 1, f3, lshift.", null);
        }

        List<String> modifiers = readModifiers(arguments);
        List<String> unknown = KeyCodes.unknownModifiers(modifiers);
        if (!unknown.isEmpty()) {
            return MCPProtocol.createErrorResponse(
                "Unknown modifier(s): " + String.join(", ", unknown) + ". Valid: ctrl, shift, alt, super.", null);
        }

        int mask = KeyCodes.modifierMask(modifiers);
        List<Integer> modifierKeys = KeyCodes.modifierKeyCodes(modifiers);
        String action = arguments.has("action") ? arguments.get("action").getAsString() : "tap";
        int holdTicks = arguments.has("hold_ticks")
            ? Math.clamp(arguments.get("hold_ticks").getAsInt(), 0, config.getInput().getMaxHoldTicks())
            : 0;
        int repeat = arguments.has("repeat") ? Math.clamp(arguments.get("repeat").getAsInt(), 1, 100) : 1;

        int key = keyCode.getAsInt();
        JsonObject result = new JsonObject();
        result.addProperty("key", keyName);
        result.addProperty("keyCode", key);
        result.addProperty("action", action);

        try {
            switch (action) {
                case "press" -> {
                    awaitClient(InputDispatcher.onClientThread(() -> {
                        InputDispatcher.holdModifiers(modifierKeys, mask);
                        InputDispatcher.pressKey(key, mask);
                    }));
                    if (holdTicks > 0) {
                        awaitRelease(key, mask, modifierKeys, holdTicks);
                        result.addProperty("heldTicks", holdTicks);
                        result.addProperty("released", true);
                    } else {
                        result.addProperty("released", false);
                        result.addProperty("note",
                            "Key is still held. Call press_key again with action 'release' to let go.");
                    }
                }
                case "release" -> awaitClient(InputDispatcher.onClientThread(() -> {
                    InputDispatcher.releaseKey(key, mask);
                    InputDispatcher.releaseModifiers(modifierKeys, mask);
                }));
                case "tap" -> {
                    if (holdTicks > 0) {
                        awaitClient(InputDispatcher.onClientThread(() -> {
                            InputDispatcher.holdModifiers(modifierKeys, mask);
                            InputDispatcher.pressKey(key, mask);
                        }));
                        awaitRelease(key, mask, modifierKeys, holdTicks);
                        result.addProperty("heldTicks", holdTicks);
                    } else {
                        for (int i = 0; i < repeat; i++) {
                            awaitClient(InputDispatcher.onClientThread(
                                () -> InputDispatcher.tapKeyWithModifiers(key, modifierKeys, mask)));
                        }
                        result.addProperty("repeated", repeat);
                    }
                }
                default -> {
                    return MCPProtocol.createErrorResponse(
                        "Unknown action '" + action + "'. Valid: tap, press, release.", null);
                }
            }
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("Failed to deliver key input: " + e.getMessage(), null);
        }

        if (!modifiers.isEmpty()) {
            JsonArray applied = new JsonArray();
            modifiers.forEach(applied::add);
            result.add("modifiers", applied);
        }
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    /** Releases after the hold elapses, and waits for that to happen so callers see a settled state. */
    private void awaitRelease(int key, int mask, List<Integer> modifierKeys, int holdTicks) throws Exception {
        CompletableFuture<Void> released = new CompletableFuture<>();
        ClientTickScheduler.runAfterTicks(holdTicks, () -> {
            try {
                InputDispatcher.releaseKey(key, mask);
                InputDispatcher.releaseModifiers(modifierKeys, mask);
                released.complete(null);
            } catch (Throwable t) {
                released.completeExceptionally(t);
            }
        });
        awaitClient(released);
    }

    private void awaitClient(CompletableFuture<Void> future) throws Exception {
        future.get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    static List<String> readModifiers(JsonObject arguments) {
        List<String> modifiers = new ArrayList<>();
        if (arguments.has("modifiers") && arguments.get("modifiers").isJsonArray()) {
            JsonArray array = arguments.getAsJsonArray("modifiers");
            for (JsonElement element : array) {
                if (element != null && element.isJsonPrimitive()) {
                    modifiers.add(element.getAsString());
                }
            }
        }
        return modifiers;
    }

    /** Exposed so sibling tools can reuse the same action constant. */
    static int pressAction() {
        return InputConstants.PRESS;
    }
}
