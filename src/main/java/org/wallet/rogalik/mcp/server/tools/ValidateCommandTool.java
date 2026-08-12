package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class ValidateCommandTool implements McpTool {

    private static final int MAX_SUGGESTIONS = 40;

    private final Supplier<MinecraftServer> serverSupplier;

    public ValidateCommandTool(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = serverSupplier;
    }

    @Override
    public String name() {
        return "validate_command";
    }

    @Override
    public String description() {
        return "Parse a command without running it, and report whether it is valid.\n\n"
            + "On a syntax error you get the message and the cursor position where parsing stopped. "
            + "With suggest=true you also get the completions available at the end of the string, "
            + "which is the same list the in-game command line offers - useful for discovering what "
            + "an argument accepts.\n\n"
            + "Use this on generated commands before writing them into a datapack function, where a "
            + "single bad line stops the whole function silently.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .string("command", "Command to parse, without the leading slash", true)
            .bool("suggest", "Also return completions available at the end of the string", false, false)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            return MCPProtocol.createErrorResponse(
                "No server is running, so there is no command dispatcher to parse against.", null);
        }
        if (!arguments.has("command")) {
            return MCPProtocol.createErrorResponse("Missing required parameter: command", null);
        }

        String raw = arguments.get("command").getAsString().trim();
        String command = raw.startsWith("/") ? raw.substring(1) : raw;
        boolean wantSuggestions = arguments.has("suggest") && arguments.get("suggest").getAsBoolean();

        JsonObject result = new JsonObject();
        result.addProperty("command", raw);

        try {
            server.submit(() -> {
                CommandSourceStack source = server.createCommandSourceStack();
                ParseResults<CommandSourceStack> parse =
                    server.getCommands().getDispatcher().parse(command, source);

                try {
                    Commands.validateParseResults(parse);
                    result.addProperty("valid", true);
                } catch (CommandSyntaxException e) {
                    result.addProperty("valid", false);
                    result.addProperty("error", e.getRawMessage().getString());
                    result.addProperty("cursor", e.getCursor());
                    if (e.getCursor() >= 0 && e.getCursor() <= command.length()) {
                        result.addProperty("parsedUpTo", command.substring(0, e.getCursor()));
                    }
                }

                if (wantSuggestions) {
                    result.add("suggestions", collectSuggestions(server, parse));
                }
                return null;
            }).get();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return MCPProtocol.createErrorResponse("Could not parse command: " + cause.getMessage(), null);
        }

        return MCPProtocol.createSuccessResponse(result.toString());
    }

    private static JsonArray collectSuggestions(MinecraftServer server, ParseResults<CommandSourceStack> parse) {
        JsonArray array = new JsonArray();
        try {
            CompletableFuture<Suggestions> future =
                server.getCommands().getDispatcher().getCompletionSuggestions(parse);

            // Runs on the server thread, so never block here: an argument type that defers its
            // suggestions to this very thread would deadlock the server.
            Suggestions suggestions = future.getNow(null);
            if (suggestions == null) {
                return array;
            }

            int added = 0;
            for (Suggestion suggestion : suggestions.getList()) {
                if (added++ >= MAX_SUGGESTIONS) {
                    break;
                }
                array.add(suggestion.getText());
            }
        } catch (Exception e) {
            // Suggestions are a convenience; a failure here must not mask the parse result.
        }
        return array;
    }
}
