package org.wallet.rogalik.mcp.input;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.tools.McpTool;
import org.wallet.rogalik.mcp.server.tools.SchemaBuilder;
import net.minecraft.client.KeyMapping;

import java.util.Locale;
import java.util.Optional;

public class ListKeybindsTool implements McpTool {

    @Override
    public String name() {
        return "list_keybinds";
    }

    @Override
    public String description() {
        return "List every key binding with its id, category and the key it is currently bound to.\n\n"
            + "Use this before send_keybind so you act on what the binding actually is rather than "
            + "assuming defaults - a player may have rebound anything, and mods add their own.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .string("filter", "Case-insensitive substring matched against the binding id or category", false)
            .bool("only_bound", "Skip bindings that are currently unbound", false, false)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        String filter = arguments.has("filter")
            ? arguments.get("filter").getAsString().toLowerCase(Locale.ROOT)
            : null;
        boolean onlyBound = arguments.has("only_bound") && arguments.get("only_bound").getAsBoolean();

        JsonArray bindings = new JsonArray();
        for (KeyMapping mapping : KeybindLookup.all()) {
            String id = mapping.getName();
            String category = KeybindLookup.categoryOf(mapping);

            if (filter != null
                && !id.toLowerCase(Locale.ROOT).contains(filter)
                && !category.contains(filter)) {
                continue;
            }
            if (onlyBound && mapping.isUnbound()) {
                continue;
            }

            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("category", category);
            entry.addProperty("unbound", mapping.isUnbound());
            entry.addProperty("isDefault", mapping.isDefault());
            entry.addProperty("currentlyDown", mapping.isDown());

            Optional<InputConstants.Key> key = KeybindLookup.boundKey(mapping);
            if (key.isPresent()) {
                entry.addProperty("boundTo", key.get().getName());
                entry.addProperty("displayName", key.get().getDisplayName().getString());
                entry.addProperty("inputType", key.get().getType().name().toLowerCase(Locale.ROOT));
                entry.addProperty("code", key.get().getValue());
            }
            bindings.add(entry);
        }

        JsonObject result = new JsonObject();
        result.add("keybinds", bindings);
        result.addProperty("count", bindings.size());
        return MCPProtocol.createSuccessResponse(result.toString());
    }
}
