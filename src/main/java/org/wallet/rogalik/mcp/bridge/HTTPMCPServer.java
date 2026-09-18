package org.wallet.rogalik.mcp.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTTPMCPServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HTTPMCPServer.class);
    private static final Gson GSON = new Gson();

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    /**
     * Matches the base64 payload of an image content block. Screenshots are megabytes long and
     * used to be written to latest.log verbatim, which buried every real error under them.
     */
    private static final Pattern IMAGE_DATA = Pattern.compile("\"data\"\\s*:\\s*\"([A-Za-z0-9+/=]{200,})\"");

    private final MCPConfig config;
    private final ToolRegistry toolRegistry;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private HttpServer httpServer;
    private ExecutorService executor;

    public HTTPMCPServer(MCPConfig config, ToolRegistry toolRegistry) {
        this.config = config;
        this.toolRegistry = toolRegistry;
    }

    public void start() throws IOException {
        if (running.get()) {
            return;
        }

        String host = config.getServer().getHost();
        if (!isLoopback(host) && !config.getAuth().isEnabled()) {
            LOGGER.error(
                "Refusing to start: host '{}' is not loopback and no auth.token is configured. "
                    + "This endpoint can run commands, inject input and write files - do not expose it "
                    + "unauthenticated. Set auth.token in mcp.json or bind to 127.0.0.1.", host);
            return;
        }

        running.set(true);

        InetSocketAddress address = new InetSocketAddress(host, config.getServer().getPort());

        httpServer = HttpServer.create(address, 0);
        httpServer.createContext("/mcp", new MCPHandler());

        executor = Executors.newCachedThreadPool();
        httpServer.setExecutor(executor);

        httpServer.start();

        LOGGER.info("HTTP MCP Server started on http://{}:{}/mcp with {} tools{}",
            host, config.getServer().getPort(), toolRegistry.size(),
            config.getAuth().isEnabled() ? " (token required)" : "");
    }

    public void stop() {
        if (running.get()) {
            running.set(false);

            if (httpServer != null) {
                httpServer.stop(0);
            }

            if (executor != null) {
                executor.shutdown();
            }

            LOGGER.info("HTTP MCP Server stopped");
        }
    }

    public int getPort() {
        return config.getServer().getPort();
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    private class MCPHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String accept = exchange.getRequestHeaders().getFirst("Accept");
                String requestBody = "";
                if ("POST".equals(method)) {
                    requestBody = readRequestBody(exchange);
                }
                LOGGER.debug("MCP request - method: {}, accept: {}, body: {}",
                    method, accept, summarizeForLog(requestBody));

                if (!isAuthorized(exchange)) {
                    sendErrorResponse(exchange, 401, "Missing or invalid Authorization bearer token", null);
                    return;
                }

                String origin = exchange.getRequestHeaders().getFirst("Origin");
                if (origin != null && !isAllowedOrigin(origin)) {
                    sendErrorResponse(exchange, 403, "Forbidden origin", null);
                    return;
                }

                if ("POST".equals(method)) {
                    handlePostRequest(exchange, requestBody);
                } else if ("GET".equals(method)) {
                    handleGetRequest(exchange);
                } else {
                    sendErrorResponse(exchange, 405, "Method not allowed", null);
                }
            } catch (Exception e) {
                LOGGER.error("Error handling MCP request", e);
                sendErrorResponse(exchange, 500, "Internal server error", null);
            }
        }

        private void handlePostRequest(HttpExchange exchange, String requestBody) throws IOException {
            String accept = exchange.getRequestHeaders().getFirst("Accept");
            if (accept == null || (!accept.contains("application/json") && !accept.contains("text/event-stream"))) {
                sendErrorResponse(exchange, 400, "Invalid Accept header", null);
                return;
            }

            try {
                JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();

                JsonObject response = handleMCPRequest(request);

                if (response != null) {
                    LOGGER.debug("MCP response: {}", summarizeForLog(GSON.toJson(response)));
                    sendJsonResponse(exchange, 200, response);
                } else {
                    // Notification - no response needed
                    sendJsonResponse(exchange, 202, new JsonObject());
                }

            } catch (Exception e) {
                LOGGER.error("Error processing MCP request: {}", summarizeForLog(requestBody), e);
                JsonElement requestId = null;
                try {
                    JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
                    requestId = request.has("id") ? request.get("id") : null;
                } catch (Exception ignored) {
                    // Unable to parse request ID
                }
                JsonObject errorResponse = createErrorResponse("Error processing request: " + e.getMessage(), requestId);
                sendJsonResponse(exchange, 400, errorResponse);
            }
        }

        private void handleGetRequest(HttpExchange exchange) throws IOException {
            String accept = exchange.getRequestHeaders().getFirst("Accept");
            if (accept != null && accept.contains("text/event-stream")) {
                sendErrorResponse(exchange, 405, "Server-Sent Events not implemented", null);
            } else {
                sendErrorResponse(exchange, 400, "GET requests require text/event-stream Accept header", null);
            }
        }

        private String readRequestBody(HttpExchange exchange) throws IOException {
            try (InputStream inputStream = exchange.getRequestBody();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                return body.toString();
            }
        }

        private void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Accept, Origin, Authorization");

            byte[] responseBytes = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        }

        private void sendErrorResponse(HttpExchange exchange, int statusCode, String message, JsonElement requestId) throws IOException {
            JsonObject errorResponse = createErrorResponse(message, requestId);
            sendJsonResponse(exchange, statusCode, errorResponse);
        }

        private boolean isAuthorized(HttpExchange exchange) {
            if (!config.getAuth().isEnabled()) {
                return true;
            }
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                return false;
            }
            return constantTimeEquals(header.substring("Bearer ".length()).trim(), config.getAuth().getToken());
        }

        private boolean isAllowedOrigin(String origin) {
            return origin.startsWith("http://localhost")
                || origin.startsWith("https://localhost")
                || origin.startsWith("http://127.0.0.1")
                || origin.startsWith("https://127.0.0.1")
                || origin.equals("null") // file:// protocol
                || origin.startsWith("file://");
        }
    }

    /**
     * Redacts image payloads and clips the rest, so logging a request or response can never
     * balloon latest.log the way a raw screenshot response did.
     */
    String summarizeForLog(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }

        Matcher matcher = IMAGE_DATA.matcher(body);
        StringBuilder redacted = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(redacted,
                Matcher.quoteReplacement("\"data\":\"<" + matcher.group(1).length() + " base64 chars omitted>\""));
        }
        matcher.appendTail(redacted);

        int limit = config.getLogs().getMaxBodyLogChars();
        if (limit > 0 && redacted.length() > limit) {
            return redacted.substring(0, limit) + "... (" + redacted.length() + " chars total)";
        }
        return redacted.toString();
    }

    private static boolean isLoopback(String host) {
        return host == null || LOOPBACK_HOSTS.contains(host.toLowerCase());
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    JsonObject handleMCPRequest(JsonObject request) {
        String method = request.get("method").getAsString();
        JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
        // JSON-RPC 2.0 allows the id to be a string, a number, or absent/null - never assume it's
        // numeric (a client-generated id like "server-discover-probe-1" is legal and common).
        JsonElement requestId = request.has("id") ? request.get("id") : null;

        // Handle notifications - no response needed
        if (method.startsWith("notifications/")) {
            LOGGER.debug("Received notification: {}", method);
            return null;
        }

        JsonObject result;
        switch (method) {
            case "initialize":
                result = handleInitialize(params);
                break;
            case "ping":
                result = handlePing();
                break;
            case "tools/list":
                result = handleToolsList();
                break;
            case "tools/call":
                result = handleToolsCall(params);
                break;
            default:
                return createErrorResponse("Unknown method: " + method, requestId);
        }

        return createSuccessResponse(result, requestId);
    }

    private JsonObject handleInitialize(JsonObject params) {
        JsonObject response = new JsonObject();

        String currentProtocolVersion = "2025-06-18"; // default
        if (params.has("protocolVersion")) {
            String clientVersion = params.get("protocolVersion").getAsString();
            if ("2025-03-26".equals(clientVersion)) {
                currentProtocolVersion = "2025-03-26";
            }
        }
        response.addProperty("protocolVersion", currentProtocolVersion);

        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        response.add("capabilities", capabilities);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "minecraft-mcp-http");
        serverInfo.addProperty("version", "1.0.0");
        response.add("serverInfo", serverInfo);

        return response;
    }

    private JsonObject handlePing() {
        JsonObject response = new JsonObject();
        response.addProperty("status", "pong");
        return response;
    }

    private JsonObject handleToolsList() {
        JsonObject response = new JsonObject();
        response.add("tools", toolRegistry.listTools());
        return response;
    }

    private JsonObject handleToolsCall(JsonObject params) {
        if (params == null || !params.has("name")) {
            return createToolError("Missing required parameter: name");
        }
        String toolName = params.get("name").getAsString();
        JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
            ? params.getAsJsonObject("arguments")
            : new JsonObject();
        return toolRegistry.dispatch(toolName, arguments);
    }

    private static JsonObject createToolError(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("isError", true);
        error.addProperty("error", message);
        return error;
    }

    private JsonObject createSuccessResponse(JsonObject result, JsonElement requestId) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (requestId != null) {
            response.add("id", requestId);
        }
        response.add("result", result);
        return response;
    }

    private JsonObject createErrorResponse(String message, JsonElement requestId) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (requestId != null) {
            response.add("id", requestId);
        }

        JsonObject error = new JsonObject();
        error.addProperty("code", -32603); // Internal error
        error.addProperty("message", message);
        response.add("error", error);

        return response;
    }
}
