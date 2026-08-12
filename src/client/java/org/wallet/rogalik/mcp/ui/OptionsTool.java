package org.wallet.rogalik.mcp.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.input.InputDispatcher;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.tools.McpTool;
import org.wallet.rogalik.mcp.server.tools.SchemaBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Reads settings, and writes one when {@code name} is supplied together with {@code value}. */
public class OptionsTool implements McpTool {

    private final MCPConfig config;

    public OptionsTool(MCPConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "options";
    }

    @Override
    public String description() {
        return "Read the game settings, or change one.\n\n"
            + "Call with no arguments to list every setting with its current value and type. Call "
            + "with 'filter' to narrow the list. Call with 'name' and 'value' to change one and "
            + "save options.txt.\n\n"
            + "Setting a value directly is more reliable than opening the options menu and "
            + "dragging a slider, which depends on layout, GUI scale and scroll position.\n\n"
            + "Common names: renderDistance, simulationDistance, fov, gamma, guiScale, "
            + "framerateLimit, graphicsPreset, particles, sensitivity.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .string("filter", "Case-insensitive substring to narrow the listing", false)
            .string("name", "Setting to change; must be paired with 'value'", false)
            .string("value", "New value. Numbers and booleans are parsed; enums are matched by name", false)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) {
            return MCPProtocol.createErrorResponse("Game options are not loaded yet.", null);
        }

        if (arguments.has("name")) {
            return setOption(client, arguments);
        }
        return listOptions(client, arguments);
    }

    private JsonObject listOptions(Minecraft client, JsonObject arguments) {
        String filter = arguments.has("filter")
            ? arguments.get("filter").getAsString().toLowerCase(Locale.ROOT)
            : null;

        JsonArray list = new JsonArray();
        for (Map.Entry<String, OptionInstance<?>> entry : OptionsAccess.all(client.options).entrySet()) {
            if (filter != null && !entry.getKey().toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            JsonObject option = new JsonObject();
            option.addProperty("name", entry.getKey());
            try {
                option.addProperty("value", OptionsAccess.describe(entry.getValue()));
                option.addProperty("type", OptionsAccess.typeOf(entry.getValue()));
            } catch (Exception e) {
                option.addProperty("error", "Could not read: " + e.getMessage());
            }
            list.add(option);
        }

        JsonObject result = new JsonObject();
        result.add("options", list);
        result.addProperty("count", list.size());
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    private JsonObject setOption(Minecraft client, JsonObject arguments) {
        if (!config.getInput().isEnabled()) {
            return MCPProtocol.createErrorResponse(
                "Changing settings is disabled. Set input.enabled to true in mcp.json.", null);
        }
        if (!arguments.has("value")) {
            return MCPProtocol.createErrorResponse("'name' must be paired with 'value'.", null);
        }

        String name = arguments.get("name").getAsString();
        OptionInstance<?> option = OptionsAccess.byName(client.options, name);
        if (option == null) {
            return MCPProtocol.createErrorResponse(
                "No setting called '" + name + "'. Call this tool without arguments to list them.", null);
        }

        String before;
        try {
            before = OptionsAccess.describe(option);
        } catch (Exception e) {
            before = "unknown";
        }

        try {
            // Some settings resize the window or rebuild chunks, so apply on the client thread.
            InputDispatcher.onClientThread(() -> {
                OptionsAccess.set(option, arguments.get("value"));
                client.options.save();
            }).get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return MCPProtocol.createErrorResponse(
                "Could not set '" + name + "': " + cause.getMessage(), null);
        }

        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.addProperty("previousValue", before);
        result.addProperty("value", OptionsAccess.describe(option));
        result.addProperty("saved", true);
        return MCPProtocol.createSuccessResponse(result.toString());
    }
}
