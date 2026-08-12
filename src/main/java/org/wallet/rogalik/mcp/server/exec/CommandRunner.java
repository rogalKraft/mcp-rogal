package org.wallet.rogalik.mcp.server.exec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.wallet.rogalik.mcp.command.ICommandExecutor;
import org.wallet.rogalik.mcp.command.SafetyValidator;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Runs commands against the server dispatcher and reports what really happened.
 *
 * <p>Used by both environments. In singleplayer the client hands it the integrated server, so a
 * client no longer has to fire a command packet into the void and infer the outcome from chat.
 */
public class CommandRunner implements ICommandExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandRunner.class);

    private final MCPConfig config;
    private final Supplier<MinecraftServer> serverSupplier;
    private final SafetyValidator safetyValidator;

    public CommandRunner(MCPConfig config, Supplier<MinecraftServer> serverSupplier) {
        this.config = config;
        this.serverSupplier = serverSupplier;
        this.safetyValidator = new SafetyValidator(config);
    }

    @Override
    public JsonObject executeCommands(JsonObject arguments) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            return MCPProtocol.createErrorResponse(
                "No server is running. Load a world (singleplayer) or start the server first.", null);
        }

        if (arguments == null || !arguments.has("commands")) {
            return MCPProtocol.createErrorResponse("Missing required parameter: commands", null);
        }

        List<String> commands = readCommands(arguments.getAsJsonArray("commands"));
        if (commands.isEmpty()) {
            return MCPProtocol.createErrorResponse("No commands supplied", null);
        }

        boolean validateSafety = !arguments.has("validate_safety")
            || arguments.get("validate_safety").getAsBoolean();
        int waitTicks = arguments.has("wait_ticks") ? Math.max(0, arguments.get("wait_ticks").getAsInt()) : 0;
        RunAs runAs = RunAs.from(arguments.has("run_as") ? arguments.getAsJsonObject("run_as") : null);

        List<CommandOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);

            if (validateSafety) {
                SafetyValidator.ValidationResult validation = safetyValidator.validate(stripSlash(command));
                if (!validation.isValid()) {
                    outcomes.add(CommandOutcome.rejectedBySafety(command, validation.getErrorMessage()));
                    skipRemaining(commands, i + 1, outcomes,
                        "Skipped because command " + (i + 1) + " failed safety validation");
                    break;
                }
            }

            CommandOutcome outcome = runOne(server, command, runAs);
            outcomes.add(outcome);

            if (waitTicks > 0) {
                ServerTicks.await(server, waitTicks, config.getServer().getRequestTimeoutMs());
            }
        }

        JsonObject response = buildResponse(commands.size(), outcomes);
        response.addProperty("executedAs", describeSource(server, runAs));
        return MCPProtocol.createSuccessResponse(response.toString());
    }

    /** Spells out whose position and permissions the batch ran with, so "~" is never a mystery. */
    private static String describeSource(MinecraftServer server, RunAs runAs) {
        String level = runAs.permissionLevel() == null
            ? "operator permissions"
            : "permission level " + runAs.permissionLevel();

        if (runAs.player() != null) {
            return "player " + runAs.player() + ", at their position, with " + level;
        }
        ServerPlayer sole = solePlayer(server);
        if (sole != null) {
            return "player " + sole.getName().getString() + " (the only one online), at their "
                + "position, with " + level;
        }
        return "server console at the world origin - relative coordinates are NOT the player's "
            + "position. Name a player with run_as to change that.";
    }

    /**
     * Executes one command on the server thread.
     *
     * <p>Parsing is validated separately from execution so a syntax error is reported as such,
     * with the cursor position, instead of being flattened into a generic failure.
     */
    public CommandOutcome runOne(MinecraftServer server, String originalCommand, RunAs runAs) {
        long startedAt = System.currentTimeMillis();
        String command = stripSlash(originalCommand);

        try {
            return server.submit(() -> {
                List<String> messages = Collections.synchronizedList(new ArrayList<>());
                AtomicBoolean success = new AtomicBoolean(false);
                AtomicInteger result = new AtomicInteger(0);

                CommandSourceStack source;
                try {
                    source = buildSource(server, runAs, messages, success, result);
                } catch (IllegalArgumentException e) {
                    return CommandOutcome.runtimeError(originalCommand, e.getMessage(), List.of(),
                        System.currentTimeMillis() - startedAt);
                }

                ParseResults<CommandSourceStack> parse =
                    server.getCommands().getDispatcher().parse(command, source);

                try {
                    Commands.validateParseResults(parse);
                } catch (CommandSyntaxException e) {
                    return CommandOutcome.parseError(originalCommand, e.getRawMessage().getString(),
                        e.getCursor(), System.currentTimeMillis() - startedAt);
                }

                try {
                    server.getCommands().performCommand(parse, command);
                } catch (Exception e) {
                    LOGGER.error("Command '{}' threw while executing", command, e);
                    return CommandOutcome.runtimeError(originalCommand,
                        e.getClass().getSimpleName() + ": " + e.getMessage(),
                        List.copyOf(messages), System.currentTimeMillis() - startedAt);
                }

                long elapsed = System.currentTimeMillis() - startedAt;
                List<String> collected = List.copyOf(messages);
                return success.get()
                    ? CommandOutcome.succeeded(originalCommand, result.get(), collected, elapsed)
                    : CommandOutcome.ranWithoutSuccess(originalCommand, result.get(), collected, elapsed);
            }).get(config.getServer().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CommandOutcome.runtimeError(originalCommand, "Interrupted while waiting for the server",
                List.of(), System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return CommandOutcome.runtimeError(originalCommand, cause.getMessage(), List.of(),
                System.currentTimeMillis() - startedAt);
        }
    }

    /**
     * Builds the source the command executes as.
     *
     * <p>{@code execute as <player>} rebinds {@code @s} but keeps the caller's permissions, so
     * a command that is gated behind an operator level still runs. Setting the permission set
     * here is the only way to see what an ordinary player would actually get.
     */
    private CommandSourceStack buildSource(
        MinecraftServer server,
        RunAs runAs,
        List<String> messages,
        AtomicBoolean success,
        AtomicInteger result
    ) {
        CommandSourceStack base;
        if (runAs.player() != null) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(runAs.player());
            if (player == null) {
                throw new IllegalArgumentException("No player named '" + runAs.player() + "' is online");
            }
            base = player.createCommandSourceStack();
        } else {
            // Anchor to the player when there is exactly one, so relative coordinates and @s mean
            // what a caller expects. The console source sits at the world origin, which silently
            // turns "~ ~ ~" into somewhere else entirely.
            ServerPlayer solePlayer = solePlayer(server);
            base = solePlayer != null
                ? solePlayer.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER)
                : server.createCommandSourceStack();
        }

        CommandSourceStack source = base.withSource(new CapturingSource(messages));

        if (runAs.permissionLevel() != null) {
            source = source.withPermission(
                LevelBasedPermissionSet.forLevel(PermissionLevel.byId(runAs.permissionLevel())));
        }

        return source.withCallback((commandSucceeded, returnValue) -> {
            success.set(commandSucceeded);
            result.set(returnValue);
        });
    }

    /**
     * The only player online, or null when there are none or several.
     *
     * <p>With several connected there is no defensible default, so those callers must name one
     * through {@code run_as} rather than have one picked for them.
     */
    private static ServerPlayer solePlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.size() == 1 ? players.get(0) : null;
    }

    /** Collects command feedback instead of broadcasting it. */
    private record CapturingSource(List<String> messages) implements CommandSource {

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }

    private static void skipRemaining(List<String> commands, int from, List<CommandOutcome> outcomes, String reason) {
        for (int i = from; i < commands.size(); i++) {
            outcomes.add(CommandOutcome.skipped(commands.get(i), reason));
        }
    }

    public static JsonObject buildResponse(int totalCommands, List<CommandOutcome> outcomes) {
        JsonObject response = new JsonObject();

        int succeeded = 0;
        int failed = 0;
        JsonArray results = new JsonArray();
        JsonArray allMessages = new JsonArray();

        for (int i = 0; i < outcomes.size(); i++) {
            CommandOutcome outcome = outcomes.get(i);
            if (outcome.success()) {
                succeeded++;
            } else {
                failed++;
            }
            results.add(outcome.toJson(i));
            outcome.messages().forEach(allMessages::add);
        }

        response.addProperty("totalCommands", totalCommands);
        response.addProperty("succeededCount", succeeded);
        response.addProperty("failedCount", failed);
        // Legacy counters, derived from the same data.
        response.addProperty("acceptedCount", (int) outcomes.stream().filter(CommandOutcome::accepted).count());
        response.addProperty("appliedCount", succeeded);
        response.add("results", results);
        response.add("chatMessages", allMessages);

        if (failed > 0) {
            response.addProperty("hint",
                "Check each result's error and errorType. errorType 'parse' means a syntax error at "
                    + "the reported cursor; a null error with success=false means the command ran but "
                    + "reported nothing - usually a function that bailed out early or a condition that "
                    + "did not match.");
        } else {
            response.addProperty("hint", "Use get_blocks_in_area to verify the built structure.");
        }
        return response;
    }

    private static List<String> readCommands(JsonArray array) {
        List<String> commands = new ArrayList<>();
        if (array == null) {
            return commands;
        }
        for (JsonElement element : array) {
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String command = element.getAsString().trim();
                if (!command.isEmpty()) {
                    commands.add(command);
                }
            }
        }
        return commands;
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    /** Identity and permission level a batch runs under. */
    public record RunAs(String player, Integer permissionLevel) {

        public static final RunAs CONSOLE = new RunAs(null, null);

        public static RunAs from(JsonObject json) {
            if (json == null) {
                return CONSOLE;
            }
            String player = json.has("player") && !json.get("player").isJsonNull()
                ? json.get("player").getAsString()
                : null;
            Integer level = null;
            if (json.has("permission_level") && !json.get("permission_level").isJsonNull()) {
                level = Math.clamp(json.get("permission_level").getAsInt(), 0, 4);
            }
            return new RunAs(player, level);
        }
    }
}
