package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holds the tools this instance exposes and routes {@code tools/call} to them.
 *
 * <p>Registration order is preserved so {@code tools/list} stays stable between calls.
 */
public class ToolRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    public ToolRegistry register(McpTool tool) {
        McpTool previous = tools.put(tool.name(), tool);
        if (previous != null) {
            LOGGER.warn("Tool '{}' was registered twice; the later registration wins", tool.name());
        }
        return this;
    }

    public ToolRegistry registerAll(Collection<? extends McpTool> newTools) {
        newTools.forEach(this::register);
        return this;
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    public Set<String> names() {
        return Set.copyOf(tools.keySet());
    }

    public int size() {
        return tools.size();
    }

    public JsonArray listTools() {
        JsonArray list = new JsonArray();
        for (McpTool tool : tools.values()) {
            JsonObject descriptor = new JsonObject();
            descriptor.addProperty("name", tool.name());
            descriptor.addProperty("description", tool.description());
            descriptor.add("inputSchema", tool.inputSchema());
            list.add(descriptor);
        }
        return list;
    }

    /**
     * Runs the named tool, turning an unknown name or a thrown exception into an MCP error
     * response rather than letting it escape to the HTTP layer.
     */
    public JsonObject dispatch(String name, JsonObject arguments) {
        McpTool tool = tools.get(name);
        if (tool == null) {
            return MCPProtocol.createErrorResponse(
                "Unknown tool: " + name + ". Available tools: " + String.join(", ", tools.keySet()), null);
        }

        try {
            return tool.call(arguments == null ? new JsonObject() : arguments);
        } catch (Exception e) {
            LOGGER.error("Tool '{}' threw", name, e);
            return MCPProtocol.createErrorResponse(
                "Tool '" + name + "' failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), null);
        }
    }
}
