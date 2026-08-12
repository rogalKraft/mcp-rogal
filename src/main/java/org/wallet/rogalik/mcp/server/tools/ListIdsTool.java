package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ListIdsTool implements McpTool {

    private static final int DEFAULT_LIMIT = 200;

    private static final Map<String, Registry<?>> REGISTRIES = new LinkedHashMap<>();

    static {
        REGISTRIES.put("block", BuiltInRegistries.BLOCK);
        REGISTRIES.put("item", BuiltInRegistries.ITEM);
        REGISTRIES.put("entity_type", BuiltInRegistries.ENTITY_TYPE);
        REGISTRIES.put("block_entity_type", BuiltInRegistries.BLOCK_ENTITY_TYPE);
        REGISTRIES.put("sound_event", BuiltInRegistries.SOUND_EVENT);
        REGISTRIES.put("mob_effect", BuiltInRegistries.MOB_EFFECT);
        REGISTRIES.put("particle_type", BuiltInRegistries.PARTICLE_TYPE);
        REGISTRIES.put("attribute", BuiltInRegistries.ATTRIBUTE);
        REGISTRIES.put("fluid", BuiltInRegistries.FLUID);
    }

    @Override
    public String name() {
        return "list_ids";
    }

    @Override
    public String description() {
        return "List the registered ids of a given kind, with an optional search filter.\n\n"
            + "Datapacks break most often on an id that does not exist - a block, item or sound "
            + "that was renamed, or never had the name it was assumed to have. Checking here is "
            + "faster than discovering it from a failed /reload.\n\n"
            + "Registries: " + String.join(", ", REGISTRIES.keySet()) + ".\n\n"
            + "For the properties a block accepts in [brackets], use describe_block_state.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .enumString("registry", "Which registry to list", true,
                REGISTRIES.keySet().toArray(new String[0]))
            .string("filter", "Case-insensitive substring, or a regular expression when regex is true", false)
            .bool("regex", "Treat filter as a regular expression", false, false)
            .integer("limit", "Maximum ids to return", false, DEFAULT_LIMIT)
            .integer("offset", "How many matches to skip, for paging", false, 0)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        if (!arguments.has("registry")) {
            return MCPProtocol.createErrorResponse("Missing required parameter: registry", null);
        }

        String registryName = arguments.get("registry").getAsString().trim().toLowerCase(Locale.ROOT);
        Registry<?> registry = REGISTRIES.get(registryName);
        if (registry == null) {
            return MCPProtocol.createErrorResponse(
                "Unknown registry '" + registryName + "'. Available: "
                    + String.join(", ", REGISTRIES.keySet()), null);
        }

        boolean useRegex = arguments.has("regex") && arguments.get("regex").getAsBoolean();
        String filter = arguments.has("filter") ? arguments.get("filter").getAsString() : null;

        Pattern pattern = null;
        String substring = null;
        if (filter != null && !filter.isBlank()) {
            if (useRegex) {
                try {
                    pattern = Pattern.compile(filter, Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException e) {
                    return MCPProtocol.createErrorResponse(
                        "Invalid regular expression: " + e.getDescription(), null);
                }
            } else {
                substring = filter.toLowerCase(Locale.ROOT);
            }
        }

        List<String> matches = new ArrayList<>();
        for (Identifier id : registry.keySet()) {
            String key = id.toString();
            if (pattern != null && !pattern.matcher(key).find()) {
                continue;
            }
            if (substring != null && !key.toLowerCase(Locale.ROOT).contains(substring)) {
                continue;
            }
            matches.add(key);
        }
        matches.sort(String::compareTo);

        int offset = arguments.has("offset") ? Math.max(0, arguments.get("offset").getAsInt()) : 0;
        int limit = arguments.has("limit")
            ? Math.clamp(arguments.get("limit").getAsInt(), 1, 5000)
            : DEFAULT_LIMIT;

        JsonArray ids = new JsonArray();
        for (int i = offset; i < Math.min(matches.size(), offset + limit); i++) {
            ids.add(matches.get(i));
        }

        JsonObject result = new JsonObject();
        result.addProperty("registry", registryName);
        result.add("ids", ids);
        result.addProperty("returned", ids.size());
        result.addProperty("totalMatches", matches.size());
        result.addProperty("offset", offset);
        if (offset + ids.size() < matches.size()) {
            result.addProperty("nextOffset", offset + ids.size());
        }
        return MCPProtocol.createSuccessResponse(result.toString());
    }
}
