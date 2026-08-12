package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.command.ICommandExecutor;
import org.wallet.rogalik.mcp.config.MCPConfig;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ExecuteCommandsTool implements McpTool {

    /** Only commands the description knows how to talk about are named in it. */
    private static final Set<String> DESCRIBABLE_COMMANDS = Set.copyOf(MCPConfig.DEFAULT_ALLOWED_COMMANDS);

    private final ICommandExecutor executor;
    private final MCPConfig config;

    public ExecuteCommandsTool(ICommandExecutor executor, MCPConfig config) {
        this.executor = executor;
        this.config = config;
    }

    @Override
    public String name() {
        return "execute_commands";
    }

    @Override
    public String description() {
        return "Execute one or more Minecraft commands sequentially. "
            + "Allowed commands: " + allowedCommandsText() + ".\n\n"
            + "Each command reports what actually happened:\n"
            + "- success: whether the command reported success\n"
            + "- result: the command's return value (the same number /execute store would capture)\n"
            + "- error / errorType: 'parse' for a syntax error (with cursor position), 'runtime' for a "
            + "failure while executing; null when the command succeeded\n"
            + "- messages: the feedback the command produced\n\n"
            + "A command that parses but does nothing comes back as success=false, so a silently "
            + "failing function or an unarmed trigger is distinguishable from a working one.\n\n"
            + "One exception worth knowing: /function reports success=false and result=0 unless the "
            + "function ends with /return. That is normal, not a failure - check the world state, or "
            + "add a /return, to tell the two apart.\n\n"
            + "Use run_as to execute at a specific permission level - 'execute as <player>' changes "
            + "@s but NOT permissions, so an operator-only path can look fine until a real player hits it.\n\n"
            + "Use get_player_info first for absolute coordinates, describe_block_state for block "
            + "property syntax, and get_blocks_in_area afterwards to verify a build.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject runAs = SchemaBuilder.object()
            .string("player", "Name of the player to run as; defaults to the server console", false)
            .integer("permission_level",
                "Permission level 0-4. 0 is an ordinary player, 2 unlocks most command blocks, 4 is full operator", false)
            .build();

        return SchemaBuilder.object()
            .arrayOf("commands", "string",
                "Minecraft commands to run in order, without the leading slash", true)
            .bool("validate_safety", "Whether to run the safety validator first", false, true)
            .integer("wait_ticks",
                "Server ticks to wait after each command. Use this when a later command reads state "
                    + "an earlier one wrote - within a single tick the read still sees the old value", false, 0)
            .nested("run_as", runAs, "Identity and permission level to execute as", false)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        return executor.executeCommands(arguments);
    }

    private String allowedCommandsText() {
        List<String> configured = (config != null && config.getServer() != null
            && config.getServer().getAllowedCommands() != null)
            ? config.getServer().getAllowedCommands()
            : MCPConfig.DEFAULT_ALLOWED_COMMANDS;

        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        for (String command : configured) {
            if (command == null) {
                continue;
            }
            String normalized = command.trim().toLowerCase();
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            if (!normalized.isEmpty() && DESCRIBABLE_COMMANDS.contains(normalized)) {
                filtered.add(normalized);
            }
        }
        return filtered.isEmpty() ? "(none configured)" : String.join(", ", filtered);
    }
}
