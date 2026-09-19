package org.wallet.rogalik.mcp.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.input.InputDispatcher;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.tools.McpTool;
import org.wallet.rogalik.mcp.server.tools.SchemaBuilder;
import org.wallet.rogalik.mcp.utils.ClientTickScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class OpenScreenTool implements McpTool {

    /** Screen name to a factory taking the screen to return to when the new one is closed. */
    private static final Map<String, Function<Screen, Screen>> SCREENS = new LinkedHashMap<>();

    static {
        SCREENS.put("title", parent -> new TitleScreen());
        SCREENS.put("pause", parent -> new PauseScreen(true));
        // The trailing boolean (whether this options screen was opened mid-game) is gone in 26.3.
        SCREENS.put("options", parent -> new OptionsScreen(parent, options()));
        SCREENS.put("options.video", parent -> new VideoSettingsScreen(parent, Minecraft.getInstance(), options()));
        SCREENS.put("options.sound", parent -> new SoundOptionsScreen(parent, options()));
        SCREENS.put("options.accessibility", parent -> new AccessibilityOptionsScreen(parent, options()));
        SCREENS.put("options.keybinds", parent -> new KeyBindsScreen(parent, options()));
        SCREENS.put("world_select", SelectWorldScreen::new);
        SCREENS.put("multiplayer", JoinMultiplayerScreen::new);
        SCREENS.put("inventory", parent -> {
            var player = Minecraft.getInstance().player;
            return player == null ? null : new InventoryScreen(player);
        });
    }

    private static net.minecraft.client.Options options() {
        return Minecraft.getInstance().options;
    }

    private final MCPConfig config;

    public OpenScreenTool(MCPConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "open_screen";
    }

    @Override
    public String description() {
        return "Open a named screen directly, without clicking through the menus to reach it.\n\n"
            + "Available: " + String.join(", ", SCREENS.keySet()) + ", close.\n\n"
            + "'close' returns to the world. For chat and the command line use send_keybind with "
            + "key.chat or key.command - those go through the game's own handling, which sets up "
            + "the input field correctly.\n\n"
            + "After opening, call get_open_screen to see what is on it. Note that changing a "
            + "setting is usually better done with set_option than by driving its slider.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .string("screen", "Screen to open, e.g. 'options.video', 'world_select', 'close'", true)
            .integer("settle_ticks", "Client ticks to wait after opening, so the screen is laid out",
                false, 2)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        if (!config.getInput().isEnabled()) {
            return MCPProtocol.createErrorResponse(
                "Simulated input is disabled. Set input.enabled to true in mcp.json.", null);
        }
        if (!arguments.has("screen")) {
            return MCPProtocol.createErrorResponse("Missing required parameter: screen", null);
        }

        String requested = arguments.get("screen").getAsString().trim().toLowerCase(Locale.ROOT);
        int settleTicks = arguments.has("settle_ticks")
            ? Math.clamp(arguments.get("settle_ticks").getAsInt(), 0, 100)
            : 2;

        boolean closing = requested.equals("close") || requested.equals("none");
        Function<Screen, Screen> factory = SCREENS.get(requested);
        if (!closing && factory == null) {
            JsonObject meta = new JsonObject();
            JsonArray available = new JsonArray();
            SCREENS.keySet().forEach(available::add);
            available.add("close");
            meta.add("available", available);
            return MCPProtocol.createErrorResponse("Unknown screen '" + requested + "'.", meta);
        }

        Screen previous = ScreenInspector.currentScreen();

        try {
            InputDispatcher.onClientThread(() -> {
                Minecraft client = Minecraft.getInstance();
                Screen next = closing ? null : factory.apply(previous);
                client.setScreenAndShow(next);
            }).get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);

            if (settleTicks > 0) {
                ClientTickScheduler.waitTicks(settleTicks)
                    .get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("Failed to open '" + requested + "': " + e.getMessage(), null);
        }

        Screen now = ScreenInspector.currentScreen();
        JsonObject result = new JsonObject();
        result.addProperty("requested", requested);
        result.addProperty("screenBefore", previous == null ? null : previous.getClass().getSimpleName());
        result.addProperty("screenAfter", now == null ? null : now.getClass().getSimpleName());

        if (!closing && now == null) {
            result.addProperty("warning", "The screen did not open. For 'inventory' the player must "
                + "be in a world.");
        } else if (now != null) {
            result.add("widgets", ScreenInspector.toJson(ScreenInspector.inspect(now)));
        }
        return MCPProtocol.createSuccessResponse(result.toString());
    }
}
