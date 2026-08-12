package org.wallet.rogalik.mcp.input;

import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.tools.McpTool;
import org.wallet.rogalik.mcp.server.tools.SchemaBuilder;

import java.util.concurrent.TimeUnit;

public class TypeTextTool implements McpTool {

    private static final int MAX_LENGTH = 2000;

    private final MCPConfig config;

    public TypeTextTool(MCPConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "type_text";
    }

    @Override
    public String description() {
        return "Type text into whatever currently has focus - a chat box, a search field, a "
            + "command input, a world name.\n\n"
            + "Character input is a separate channel from key events, so press_key alone types "
            + "nothing: use press_key to open the field and to press enter, and this tool for the "
            + "text between.\n\n"
            + "Typical chat sequence: press_key('t'), type_text('hello'), press_key('enter').";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .string("text", "Text to type into the focused field", true)
            .bool("press_enter", "Press enter after typing, to submit", false, false)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        if (!config.getInput().isEnabled()) {
            return MCPProtocol.createErrorResponse(
                "Simulated input is disabled. Set input.enabled to true in mcp.json.", null);
        }
        if (!arguments.has("text")) {
            return MCPProtocol.createErrorResponse("Missing required parameter: text", null);
        }

        String text = arguments.get("text").getAsString();
        if (text.length() > MAX_LENGTH) {
            return MCPProtocol.createErrorResponse(
                "Text is too long (" + text.length() + " characters, maximum " + MAX_LENGTH + ")", null);
        }

        boolean pressEnter = arguments.has("press_enter") && arguments.get("press_enter").getAsBoolean();

        try {
            InputDispatcher.onClientThread(() -> {
                InputDispatcher.typeText(text);
                if (pressEnter) {
                    InputDispatcher.pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0);
                    InputDispatcher.releaseKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0);
                }
            }).get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("Failed to type text: " + e.getMessage(), null);
        }

        JsonObject result = new JsonObject();
        result.addProperty("typed", text);
        result.addProperty("characters", text.codePointCount(0, text.length()));
        result.addProperty("pressedEnter", pressEnter);
        result.addProperty("note", "Text goes to whatever has focus. Check with get_open_screen if unsure.");
        return MCPProtocol.createSuccessResponse(result.toString());
    }
}
