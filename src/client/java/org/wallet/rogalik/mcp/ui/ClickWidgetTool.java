package org.wallet.rogalik.mcp.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.input.InputDispatcher;
import org.wallet.rogalik.mcp.input.KeyCodes;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.tools.McpTool;
import org.wallet.rogalik.mcp.server.tools.SchemaBuilder;
import org.wallet.rogalik.mcp.utils.ClientTickScheduler;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

public class ClickWidgetTool implements McpTool {

    private final MCPConfig config;

    public ClickWidgetTool(MCPConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "click_widget";
    }

    @Override
    public String description() {
        return "Click a widget on the open screen, found by its visible label or its index from "
            + "get_open_screen.\n\n"
            + "The click is delivered as a real mouse move, press and release at the widget's "
            + "centre, so everything vanilla does still happens: hover, focus, the click sound and "
            + "any screen-specific handling.\n\n"
            + "Label matching prefers an exact case-insensitive match, then a unique substring. If "
            + "several widgets match, nothing is clicked and the candidates are returned - picking "
            + "the wrong button in a menu is not recoverable.\n\n"
            + "Buttons whose label looks destructive (delete, erase, reset world) are refused "
            + "unless allow_destructive is set and input.allowDestructiveUi is enabled in the config.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .string("label", "Visible text of the widget to click", false)
            .integer("index", "Widget index from get_open_screen, as an alternative to label", false)
            .enumString("button", "Mouse button to click with", false, "left", "right", "middle")
            .bool("allow_destructive", "Permit clicking a widget whose label looks destructive", false, false)
            .integer("settle_ticks", "Client ticks to wait after clicking, so the next screen is ready. "
                + "Screens that fade out need more than the default", false, 5)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        if (!config.getInput().isEnabled()) {
            return MCPProtocol.createErrorResponse(
                "Simulated input is disabled. Set input.enabled to true in mcp.json.", null);
        }

        Screen screen = ScreenInspector.currentScreen();
        if (screen == null) {
            return MCPProtocol.createErrorResponse(
                "No menu is open, so there is nothing to click. Use open_screen or send_keybind first.", null);
        }

        boolean hasLabel = arguments.has("label");
        boolean hasIndex = arguments.has("index");
        if (hasLabel == hasIndex) {
            return MCPProtocol.createErrorResponse(
                "Provide exactly one of 'label' or 'index'.", null);
        }

        List<ScreenInspector.WidgetInfo> widgets = ScreenInspector.inspect(screen);
        ScreenInspector.WidgetInfo target;

        if (hasIndex) {
            int index = arguments.get("index").getAsInt();
            if (index < 0 || index >= widgets.size()) {
                return MCPProtocol.createErrorResponse(
                    "Index " + index + " is out of range; the screen has " + widgets.size() + " widgets.", null);
            }
            target = widgets.get(index);
        } else {
            ScreenInspector.Match match = ScreenInspector.findByLabel(widgets, arguments.get("label").getAsString());
            if (match.isAmbiguous()) {
                JsonObject error = new JsonObject();
                error.add("candidates", ScreenInspector.toJson(match.candidates()));
                return MCPProtocol.createErrorResponse(
                    "'" + arguments.get("label").getAsString() + "' matches " + match.candidates().size()
                        + " widgets. Use a more specific label, or pass the index from get_open_screen.",
                    error);
            }
            if (!match.isFound()) {
                return MCPProtocol.createErrorResponse(
                    "No widget labelled '" + arguments.get("label").getAsString() + "' on "
                        + screen.getClass().getSimpleName() + ". Call get_open_screen to see what is there.", null);
            }
            target = match.widget();
        }

        if (!target.isClickable()) {
            return MCPProtocol.createErrorResponse(
                "Widget '" + target.text() + "' cannot be clicked (active=" + target.active()
                    + ", visible=" + target.visible() + ", hasBounds=" + (target.x() != null) + ").", null);
        }

        boolean allowDestructive = arguments.has("allow_destructive")
            && arguments.get("allow_destructive").getAsBoolean();
        Optional<String> destructive = DestructiveActions.reasonFor(target.text());
        if (destructive.isPresent() && !(allowDestructive && config.getInput().isAllowDestructiveUi())) {
            return MCPProtocol.createErrorResponse(
                "Refusing to click '" + target.text() + "': " + destructive.get()
                    + ". To go ahead, set input.allowDestructiveUi in mcp.json and pass allow_destructive.", null);
        }

        OptionalInt button = KeyCodes.mouseButtonByName(
            arguments.has("button") ? arguments.get("button").getAsString() : null);
        if (button.isEmpty()) {
            return MCPProtocol.createErrorResponse("Unknown mouse button", null);
        }

        int centerX = target.x() + target.width() / 2;
        int centerY = target.y() + target.height() / 2;
        int settleTicks = arguments.has("settle_ticks")
            ? Math.clamp(arguments.get("settle_ticks").getAsInt(), 0, 100)
            : 5;

        try {
            InputDispatcher.onClientThread(() -> {
                InputDispatcher.moveMouse(
                    InputDispatcher.guiToWindowX(centerX), InputDispatcher.guiToWindowY(centerY));
                InputDispatcher.clickMouse(button.getAsInt(), 0);
            }).get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);

            if (settleTicks > 0) {
                ClientTickScheduler.waitTicks(settleTicks)
                    .get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("Failed to click widget: " + e.getMessage(), null);
        }

        JsonObject result = new JsonObject();
        result.addProperty("clicked", target.text());
        result.addProperty("index", target.index());
        result.addProperty("type", target.type());
        result.addProperty("guiX", centerX);
        result.addProperty("guiY", centerY);

        Screen after = ScreenInspector.currentScreen();
        result.addProperty("screenBefore", screen.getClass().getSimpleName());
        result.addProperty("screenAfter", after == null ? null : after.getClass().getSimpleName());
        result.addProperty("screenChanged", after != screen);

        if (after == screen) {
            JsonArray now = ScreenInspector.toJson(ScreenInspector.inspect(after));
            result.add("widgetsAfter", now);
            result.addProperty("note", "The screen did not change yet. For a toggle or slider that is "
                + "expected - check the widget's text for its new state. Screens that fade out, such "
                + "as the onboarding and title screens, switch a few ticks later: raise settle_ticks "
                + "or call get_open_screen again.");
        }
        return MCPProtocol.createSuccessResponse(result.toString());
    }
}
