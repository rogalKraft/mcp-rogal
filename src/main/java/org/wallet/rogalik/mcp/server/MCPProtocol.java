package org.wallet.rogalik.mcp.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Builders for MCP tool-result content blocks.
 *
 * <p>Tool descriptions and schemas used to live here too; they now sit on each
 * {@link org.wallet.rogalik.mcp.server.tools.McpTool} implementation, which is what
 * {@link org.wallet.rogalik.mcp.server.tools.ToolRegistry} enumerates.
 */
public class MCPProtocol {

    public static JsonObject createSuccessResponse(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("isError", false);

        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", message);
        content.add(textContent);

        response.add("content", content);
        return response;
    }

    public static JsonObject createErrorResponse(String message, JsonObject meta) {
        JsonObject response = new JsonObject();
        response.addProperty("isError", true);

        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", message);
        content.add(textContent);

        response.add("content", content);
        if (meta != null) {
            response.add("_meta", meta);
        }

        return response;
    }

    public static JsonObject createImageResponse(String base64Data, String mimeType) {
        JsonObject response = new JsonObject();
        response.addProperty("isError", false);

        JsonArray content = new JsonArray();
        JsonObject imageContent = new JsonObject();
        imageContent.addProperty("type", "image");
        imageContent.addProperty("data", base64Data);
        imageContent.addProperty("mimeType", mimeType);
        content.add(imageContent);

        response.add("content", content);
        return response;
    }
}
