package org.wallet.rogalik.mcp.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.tools.McpTool;
import org.wallet.rogalik.mcp.server.tools.SchemaBuilder;
import org.wallet.rogalik.mcp.server.tools.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class HTTPMCPServerTest {

    private static final Gson GSON = new Gson();

    private static class EchoTool implements McpTool {
        private final String name;
        private final AtomicReference<JsonObject> received = new AtomicReference<>();

        EchoTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "echoes";
        }

        @Override
        public JsonObject inputSchema() {
            return SchemaBuilder.empty();
        }

        @Override
        public JsonObject call(JsonObject arguments) {
            received.set(arguments);
            return MCPProtocol.createSuccessResponse("echo:" + name);
        }
    }

    private static HTTPMCPServer serverWith(McpTool... tools) {
        ToolRegistry registry = new ToolRegistry();
        for (McpTool tool : tools) {
            registry.register(tool);
        }
        return new HTTPMCPServer(new MCPConfig(), registry);
    }

    private static JsonObject request(int id, String method, JsonObject params) {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", id);
        request.addProperty("method", method);
        if (params != null) {
            request.add("params", params);
        }
        return request;
    }

    private static String extractText(JsonObject response) {
        JsonArray content = response.getAsJsonArray("content");
        return content.get(0).getAsJsonObject().get("text").getAsString();
    }

    @Test
    public void toolsListReportsWhateverIsRegistered() {
        HTTPMCPServer server = serverWith(new EchoTool("alpha"), new EchoTool("beta"));

        JsonObject response = server.handleMCPRequest(request(1, "tools/list", new JsonObject()));

        JsonArray tools = response.getAsJsonObject("result").getAsJsonArray("tools");
        assertEquals(2, tools.size());
        assertEquals("alpha", tools.get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    public void toolsCallRoutesArgumentsToTheTool() {
        EchoTool alpha = new EchoTool("alpha");
        HTTPMCPServer server = serverWith(alpha);

        JsonObject params = new JsonObject();
        params.addProperty("name", "alpha");
        JsonObject args = new JsonObject();
        args.addProperty("x", 5);
        params.add("arguments", args);

        JsonObject response = server.handleMCPRequest(request(2, "tools/call", params));

        assertEquals("2.0", response.get("jsonrpc").getAsString());
        assertEquals(2, response.get("id").getAsInt());
        JsonObject result = response.getAsJsonObject("result");
        assertFalse(result.get("isError").getAsBoolean());
        assertEquals("echo:alpha", extractText(result));
        assertEquals(5, alpha.received.get().get("x").getAsInt());
    }

    @Test
    public void toolsCallWithoutArgumentsPassesAnEmptyObject() {
        EchoTool alpha = new EchoTool("alpha");
        HTTPMCPServer server = serverWith(alpha);

        JsonObject params = new JsonObject();
        params.addProperty("name", "alpha");

        JsonObject response = server.handleMCPRequest(request(3, "tools/call", params));

        assertFalse(response.getAsJsonObject("result").get("isError").getAsBoolean());
        assertNotNull(alpha.received.get());
        assertTrue(alpha.received.get().isEmpty());
    }

    @Test
    public void callingAnUnregisteredToolIsAnError() {
        // A tool that is unavailable in this environment is simply never registered,
        // which is how take_screenshot behaves on a dedicated server.
        HTTPMCPServer server = serverWith(new EchoTool("alpha"));

        JsonObject params = new JsonObject();
        params.addProperty("name", "take_screenshot");
        params.add("arguments", new JsonObject());

        JsonObject result = server.handleMCPRequest(request(4, "tools/call", params)).getAsJsonObject("result");

        assertTrue(result.get("isError").getAsBoolean());
        assertTrue(extractText(result).contains("Unknown tool: take_screenshot"));
    }

    @Test
    public void notificationsProduceNoResponse() {
        HTTPMCPServer server = serverWith();

        assertNull(server.handleMCPRequest(request(5, "notifications/initialized", new JsonObject())));
    }

    @Test
    public void unknownMethodReportsAJsonRpcError() {
        HTTPMCPServer server = serverWith();

        JsonObject response = server.handleMCPRequest(request(6, "does/not/exist", new JsonObject()));

        assertTrue(response.has("error"));
        assertTrue(response.getAsJsonObject("error").get("message").getAsString().contains("does/not/exist"));
    }

    @Test
    public void initializeEchoesBackASupportedProtocolVersion() {
        HTTPMCPServer server = serverWith();

        JsonObject params = new JsonObject();
        params.addProperty("protocolVersion", "2025-03-26");
        JsonObject result = server.handleMCPRequest(request(7, "initialize", params)).getAsJsonObject("result");

        assertEquals("2025-03-26", result.get("protocolVersion").getAsString());
        assertTrue(result.getAsJsonObject("capabilities").has("tools"));
    }

    @Test
    public void screenshotPayloadsAreRedactedBeforeLogging() {
        HTTPMCPServer server = serverWith();
        String base64 = "A".repeat(4000);
        String body = "{\"content\":[{\"type\":\"image\",\"data\":\"" + base64 + "\",\"mimeType\":\"image/png\"}]}";

        String summarized = server.summarizeForLog(body);

        assertFalse(summarized.contains(base64), "Raw base64 must never reach the log");
        assertTrue(summarized.contains("base64 chars omitted"), summarized);
    }

    @Test
    public void longBodiesAreClippedToTheConfiguredLimit() {
        MCPConfig config = GSON.fromJson("{\"logs\":{\"maxBodyLogChars\":50}}", MCPConfig.class);
        HTTPMCPServer server = new HTTPMCPServer(config, new ToolRegistry());

        String summarized = server.summarizeForLog("x".repeat(500));

        assertTrue(summarized.startsWith("x".repeat(50)));
        assertTrue(summarized.contains("500 chars total"), summarized);
    }

    @Test
    public void shortBodiesArePassedThroughUnchanged() {
        HTTPMCPServer server = serverWith();
        String body = "{\"method\":\"ping\"}";

        assertEquals(body, server.summarizeForLog(body));
    }
}
