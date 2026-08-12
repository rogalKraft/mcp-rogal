package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonObject;

/**
 * One MCP tool: its advertised schema and its implementation.
 *
 * <p>Availability is decided by registration rather than by a flag on the tool — the client and
 * dedicated-server entry points each register the set that makes sense for them, so a tool that
 * is not applicable simply never appears in {@code tools/list}.
 */
public interface McpTool {

    String name();

    String description();

    JsonObject inputSchema();

    /**
     * Runs the tool.
     *
     * @param arguments the {@code arguments} object from the {@code tools/call} request; never
     *                  null, but may be empty
     * @return an MCP content response, built with the {@code MCPProtocol.create*Response} helpers
     */
    JsonObject call(JsonObject arguments);
}
