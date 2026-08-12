package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

public class DescribeBlockStateTool implements McpTool {

    @Override
    public String name() {
        return "describe_block_state";
    }

    @Override
    public String description() {
        return "Describe the block-state properties a block accepts: every property name, its "
            + "allowed values, and the default.\n\n"
            + "This is the authoritative answer to what goes inside the [brackets] of a setblock "
            + "or fill command. Guessing produces a syntax error at best and a wrong-looking build "
            + "at worst - a door placed without its upper half, or stairs facing the wrong way.\n\n"
            + "Example: describe_block_state('oak_door') reports facing, half, hinge, open and "
            + "powered together with their valid values.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .string("block", "Block id, with or without the minecraft: namespace", true)
            .bool("include_example", "Include a ready-to-use setblock example", false, true)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        if (!arguments.has("block")) {
            return MCPProtocol.createErrorResponse("Missing required parameter: block", null);
        }

        String requested = arguments.get("block").getAsString().trim();
        Identifier id;
        try {
            id = requested.contains(":") ? Identifier.parse(requested) : Identifier.withDefaultNamespace(requested);
        } catch (Exception e) {
            return MCPProtocol.createErrorResponse("'" + requested + "' is not a valid id: " + e.getMessage(), null);
        }

        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            return MCPProtocol.createErrorResponse(
                "No block called '" + id + "'. Use list_ids with registry 'block' and a filter to find it.", null);
        }

        Block block = BuiltInRegistries.BLOCK.getValue(id);
        BlockState defaultState = block.defaultBlockState();

        JsonArray properties = new JsonArray();
        List<String> exampleParts = new ArrayList<>();

        for (Property<?> property : block.getStateDefinition().getProperties()) {
            JsonObject descriptor = new JsonObject();
            descriptor.addProperty("name", property.getName());
            descriptor.addProperty("type", property.getValueClass().getSimpleName().toLowerCase());

            JsonArray values = new JsonArray();
            for (Object value : property.getPossibleValues()) {
                values.add(nameOf(property, value));
            }
            descriptor.add("values", values);

            String defaultValue = nameOf(property, defaultState.getValue(property));
            descriptor.addProperty("default", defaultValue);
            properties.add(descriptor);
            exampleParts.add(property.getName() + "=" + defaultValue);
        }

        JsonObject result = new JsonObject();
        result.addProperty("block", id.toString());
        result.add("properties", properties);
        result.addProperty("propertyCount", properties.size());
        result.addProperty("possibleStates", block.getStateDefinition().getPossibleStates().size());

        if (properties.isEmpty()) {
            result.addProperty("note", "This block has no state properties; use it bare, with no brackets.");
        } else if (!arguments.has("include_example") || arguments.get("include_example").getAsBoolean()) {
            result.addProperty("example",
                "setblock ~ ~ ~ " + id + "[" + String.join(",", exampleParts) + "]");
        }

        if (block.getStateDefinition().getProperty("half") != null) {
            result.addProperty("hint", "Blocks with a 'half' property occupy two positions. Place both "
                + "parts, or the block breaks immediately.");
        }
        return MCPProtocol.createSuccessResponse(result.toString());
    }

    /** Property values render by their serialized name, which is what commands accept. */
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String nameOf(Property<T> property, Object value) {
        return property.getName((T) value);
    }
}
