package org.wallet.rogalik.mcp.input;

import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.tools.McpTool;
import org.wallet.rogalik.mcp.server.tools.SchemaBuilder;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

public class MouseTool implements McpTool {

    private final MCPConfig config;

    public MouseTool(MCPConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "mouse";
    }

    @Override
    public String description() {
        return "Move, click, scroll or drag the mouse.\n\n"
            + "Coordinates default to GUI space - the same space get_open_screen reports widget "
            + "rectangles in - so a widget at x=120,y=80 is clicked at x=120,y=80. Pass "
            + "coord_space 'window' to use raw pixels instead, which is what a screenshot shows.\n\n"
            + "For clicking a button in a menu, prefer click_widget: it finds the widget by its "
            + "visible label and clicks its centre, so it survives layout changes and different "
            + "GUI scales.\n\n"
            + "Actions: move, click (press+release), press, release, scroll, drag (press at the "
            + "current position, move to x/y, release).";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .enumString("action", "What to do", true,
                "move", "click", "press", "release", "scroll", "drag")
            .number("x", "Horizontal position; required for move, drag, and for click at a position", false)
            .number("y", "Vertical position", false)
            .enumString("button", "Which button", false, "left", "right", "middle")
            .enumString("coord_space", "How x and y are interpreted", false, "gui", "window")
            .number("scroll_x", "Horizontal scroll amount", false)
            .number("scroll_y", "Vertical scroll amount; positive scrolls up", false)
            .arrayOf("modifiers", "string", "Modifiers to hold: ctrl, shift, alt, super", false)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        if (!config.getInput().isEnabled()) {
            return MCPProtocol.createErrorResponse(
                "Simulated input is disabled. Set input.enabled to true in mcp.json.", null);
        }
        if (!arguments.has("action")) {
            return MCPProtocol.createErrorResponse("Missing required parameter: action", null);
        }

        String action = arguments.get("action").getAsString();

        OptionalInt button = KeyCodes.mouseButtonByName(
            arguments.has("button") ? arguments.get("button").getAsString() : null);
        if (button.isEmpty()) {
            return MCPProtocol.createErrorResponse(
                "Unknown mouse button. Valid: " + String.join(", ", KeyCodes.knownMouseButtons()), null);
        }

        List<String> modifiers = PressKeyTool.readModifiers(arguments);
        List<String> unknownModifiers = KeyCodes.unknownModifiers(modifiers);
        if (!unknownModifiers.isEmpty()) {
            return MCPProtocol.createErrorResponse(
                "Unknown modifier(s): " + String.join(", ", unknownModifiers), null);
        }
        int mask = KeyCodes.modifierMask(modifiers);

        boolean useWindowSpace = arguments.has("coord_space")
            && "window".equalsIgnoreCase(arguments.get("coord_space").getAsString());

        boolean hasPosition = arguments.has("x") && arguments.has("y");
        if ((action.equals("move") || action.equals("drag")) && !hasPosition) {
            return MCPProtocol.createErrorResponse("Action '" + action + "' requires both x and y", null);
        }

        JsonObject result = new JsonObject();
        result.addProperty("action", action);

        try {
            switch (action) {
                case "move" -> {
                    double[] target = resolve(arguments, useWindowSpace);
                    runAndWait(() -> InputDispatcher.moveMouse(target[0], target[1]));
                    describePosition(result, target, useWindowSpace);
                }
                case "click", "press", "release" -> {
                    double[] target = hasPosition ? resolve(arguments, useWindowSpace) : null;
                    runAndWait(() -> {
                        if (target != null) {
                            InputDispatcher.moveMouse(target[0], target[1]);
                        }
                        switch (action) {
                            case "click" -> InputDispatcher.clickMouse(button.getAsInt(), mask);
                            case "press" -> InputDispatcher.mouseButton(button.getAsInt(), mask, GLFW.GLFW_PRESS);
                            default -> InputDispatcher.mouseButton(button.getAsInt(), mask, GLFW.GLFW_RELEASE);
                        }
                    });
                    if (target != null) {
                        describePosition(result, target, useWindowSpace);
                    }
                    result.addProperty("button", arguments.has("button")
                        ? arguments.get("button").getAsString() : "left");
                }
                case "scroll" -> {
                    double scrollX = arguments.has("scroll_x") ? arguments.get("scroll_x").getAsDouble() : 0;
                    double scrollY = arguments.has("scroll_y") ? arguments.get("scroll_y").getAsDouble() : 0;
                    if (scrollX == 0 && scrollY == 0) {
                        return MCPProtocol.createErrorResponse(
                            "Scrolling needs a non-zero scroll_x or scroll_y", null);
                    }
                    runAndWait(() -> InputDispatcher.scroll(scrollX, scrollY));
                    result.addProperty("scrollX", scrollX);
                    result.addProperty("scrollY", scrollY);
                }
                case "drag" -> {
                    double[] target = resolve(arguments, useWindowSpace);
                    runAndWait(() -> {
                        InputDispatcher.mouseButton(button.getAsInt(), mask, GLFW.GLFW_PRESS);
                        InputDispatcher.moveMouse(target[0], target[1]);
                        InputDispatcher.mouseButton(button.getAsInt(), mask, GLFW.GLFW_RELEASE);
                    });
                    describePosition(result, target, useWindowSpace);
                }
                default -> {
                    return MCPProtocol.createErrorResponse(
                        "Unknown action '" + action + "'. Valid: move, click, press, release, scroll, drag.", null);
                }
            }
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("Failed to deliver mouse input: " + e.getMessage(), null);
        }

        result.addProperty("guiScale", Minecraft.getInstance().getWindow().getGuiScale());
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    /** Returns the target in raw window pixels, converting from GUI space when needed. */
    private static double[] resolve(JsonObject arguments, boolean useWindowSpace) {
        double x = arguments.get("x").getAsDouble();
        double y = arguments.get("y").getAsDouble();
        if (useWindowSpace) {
            return new double[]{x, y};
        }
        return new double[]{InputDispatcher.guiToWindowX(x), InputDispatcher.guiToWindowY(y)};
    }

    private static void describePosition(JsonObject result, double[] windowCoords, boolean useWindowSpace) {
        result.addProperty("windowX", windowCoords[0]);
        result.addProperty("windowY", windowCoords[1]);
        result.addProperty("guiX", InputDispatcher.windowToGuiX(windowCoords[0]));
        result.addProperty("guiY", InputDispatcher.windowToGuiY(windowCoords[1]));
        result.addProperty("coordSpace", useWindowSpace ? "window" : "gui");
    }

    private void runAndWait(Runnable action) throws Exception {
        InputDispatcher.onClientThread(action)
            .get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
    }
}
