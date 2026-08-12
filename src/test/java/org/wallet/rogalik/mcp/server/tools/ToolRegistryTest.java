package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ToolRegistryTest {

    /** Minimal tool that records the arguments it was handed. */
    private static class RecordingTool implements McpTool {
        private final String name;
        private final AtomicReference<JsonObject> received = new AtomicReference<>();
        private RuntimeException toThrow;

        RecordingTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "description of " + name;
        }

        @Override
        public JsonObject inputSchema() {
            return SchemaBuilder.empty();
        }

        @Override
        public JsonObject call(JsonObject arguments) {
            received.set(arguments);
            if (toThrow != null) {
                throw toThrow;
            }
            return MCPProtocol.createSuccessResponse("ran " + name);
        }
    }

    private static String textOf(JsonObject response) {
        JsonArray content = response.getAsJsonArray("content");
        return content.get(0).getAsJsonObject().get("text").getAsString();
    }

    @Test
    public void dispatchRoutesToTheNamedTool() {
        RecordingTool alpha = new RecordingTool("alpha");
        RecordingTool beta = new RecordingTool("beta");
        ToolRegistry registry = new ToolRegistry().register(alpha).register(beta);

        JsonObject args = new JsonObject();
        args.addProperty("value", 42);

        JsonObject response = registry.dispatch("beta", args);

        assertFalse(response.get("isError").getAsBoolean());
        assertEquals("ran beta", textOf(response));
        assertEquals(42, beta.received.get().get("value").getAsInt());
        assertNull(alpha.received.get(), "The other tool must not be invoked");
    }

    @Test
    public void unknownToolReturnsErrorListingWhatIsAvailable() {
        ToolRegistry registry = new ToolRegistry().register(new RecordingTool("alpha"));

        JsonObject response = registry.dispatch("nope", new JsonObject());

        assertTrue(response.get("isError").getAsBoolean());
        String text = textOf(response);
        assertTrue(text.contains("Unknown tool: nope"), text);
        assertTrue(text.contains("alpha"), "Error should name the available tools: " + text);
    }

    @Test
    public void thrownExceptionBecomesAnErrorResponseRatherThanEscaping() {
        RecordingTool tool = new RecordingTool("boom");
        tool.toThrow = new IllegalStateException("player is not available");
        ToolRegistry registry = new ToolRegistry().register(tool);

        JsonObject response = assertDoesNotThrow(() -> registry.dispatch("boom", new JsonObject()));

        assertTrue(response.get("isError").getAsBoolean());
        assertTrue(textOf(response).contains("player is not available"));
    }

    @Test
    public void nullArgumentsAreNormalisedToAnEmptyObject() {
        RecordingTool tool = new RecordingTool("alpha");
        new ToolRegistry().register(tool).dispatch("alpha", null);

        assertNotNull(tool.received.get());
        assertTrue(tool.received.get().isEmpty());
    }

    @Test
    public void listToolsPreservesRegistrationOrderAndCarriesSchema() {
        ToolRegistry registry = new ToolRegistry()
            .register(new RecordingTool("first"))
            .register(new RecordingTool("second"));

        JsonArray tools = registry.listTools();

        assertEquals(2, tools.size());
        assertEquals("first", tools.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("second", tools.get(1).getAsJsonObject().get("name").getAsString());
        assertEquals("description of first", tools.get(0).getAsJsonObject().get("description").getAsString());
        assertEquals("object",
            tools.get(0).getAsJsonObject().getAsJsonObject("inputSchema").get("type").getAsString());
    }

    @Test
    public void registeringTheSameNameTwiceKeepsTheLaterTool() {
        RecordingTool original = new RecordingTool("dup");
        RecordingTool replacement = new RecordingTool("dup");
        ToolRegistry registry = new ToolRegistry().register(original).register(replacement);

        registry.dispatch("dup", new JsonObject());

        assertEquals(1, registry.size());
        assertNotNull(replacement.received.get());
        assertNull(original.received.get());
    }

    @Test
    public void registerAllAddsEveryTool() {
        ToolRegistry registry = new ToolRegistry()
            .registerAll(List.of(new RecordingTool("a"), new RecordingTool("b")));

        assertEquals(2, registry.size());
        assertTrue(registry.has("a"));
        assertTrue(registry.has("b"));
    }
}
