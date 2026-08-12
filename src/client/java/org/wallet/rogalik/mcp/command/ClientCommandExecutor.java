package org.wallet.rogalik.mcp.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.exec.CommandOutcome;
import org.wallet.rogalik.mcp.server.exec.CommandRunner;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side entry point for {@code execute_commands}.
 *
 * <p>In singleplayer the client hosts an integrated server, so commands go straight to the
 * dispatcher and come back with a real success flag, return value and parse diagnostics. Only
 * when connected to someone else's server does the client fall back to firing a command packet,
 * where the protocol genuinely gives it no way to observe the result.
 */
public class ClientCommandExecutor implements ICommandExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientCommandExecutor.class);

    /** How long to collect chat feedback for a command sent to a remote server. */
    private static final long REMOTE_FEEDBACK_WINDOW_MS = 500L;

    private final MCPConfig config;
    private final CommandRunner runner;
    private final SafetyValidator safetyValidator;

    public ClientCommandExecutor(MCPConfig config) {
        this.config = config;
        this.runner = new CommandRunner(config, () -> Minecraft.getInstance().getSingleplayerServer());
        this.safetyValidator = new SafetyValidator(config);
    }

    @Override
    public JsonObject executeCommands(JsonObject arguments) {
        Minecraft client = Minecraft.getInstance();

        if (client.getSingleplayerServer() != null) {
            return runner.executeCommands(arguments);
        }

        if (client.getConnection() == null) {
            return MCPProtocol.createErrorResponse(
                "Not connected to a world. Load a singleplayer world or join a server first.", null);
        }
        return executeRemotely(arguments);
    }

    private JsonObject executeRemotely(JsonObject arguments) {
        if (arguments == null || !arguments.has("commands")) {
            return MCPProtocol.createErrorResponse("Missing required parameter: commands", null);
        }

        List<String> commands = new ArrayList<>();
        for (JsonElement element : arguments.getAsJsonArray("commands")) {
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String command = element.getAsString().trim();
                if (!command.isEmpty()) {
                    commands.add(command);
                }
            }
        }
        if (commands.isEmpty()) {
            return MCPProtocol.createErrorResponse("No commands supplied", null);
        }

        boolean validateSafety = !arguments.has("validate_safety")
            || arguments.get("validate_safety").getAsBoolean();

        ChatMessageCapture capture = ChatMessageCapture.getInstance();
        capture.startCapturing();
        List<CommandOutcome> outcomes = new ArrayList<>();

        try {
            for (int i = 0; i < commands.size(); i++) {
                String command = commands.get(i);
                String withoutSlash = command.startsWith("/") ? command.substring(1) : command;

                if (validateSafety) {
                    SafetyValidator.ValidationResult validation = safetyValidator.validate(withoutSlash);
                    if (!validation.isValid()) {
                        outcomes.add(CommandOutcome.rejectedBySafety(command, validation.getErrorMessage()));
                        for (int j = i + 1; j < commands.size(); j++) {
                            outcomes.add(CommandOutcome.skipped(commands.get(j),
                                "Skipped because command " + (i + 1) + " failed safety validation"));
                        }
                        break;
                    }
                }

                outcomes.add(sendOne(command, withoutSlash, capture));
            }
        } finally {
            capture.stopCapturing();
        }

        JsonObject response = CommandRunner.buildResponse(commands.size(), outcomes);
        response.addProperty("hint",
            "Connected to a remote server, so command results cannot be observed from the client. "
                + "For real success/failure reporting, use singleplayer or install this mod on the server.");
        return MCPProtocol.createSuccessResponse(response.toString());
    }

    private CommandOutcome sendOne(String originalCommand, String command, ChatMessageCapture capture) {
        long startedAt = System.currentTimeMillis();
        Minecraft client = Minecraft.getInstance();

        capture.drainAvailableCapturedMessages();
        client.execute(() -> {
            try {
                if (client.getConnection() != null) {
                    client.getConnection().sendCommand(command);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to send command '{}' to the server", command, e);
            }
        });

        try {
            Thread.sleep(REMOTE_FEEDBACK_WINDOW_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<String> messages = new ArrayList<>();
        for (ChatMessageCapture.CapturedMessage captured : capture.drainAvailableCapturedMessages()) {
            if (captured.text() != null && !captured.text().isBlank()) {
                messages.add(captured.text());
            }
        }

        return CommandOutcome.sentToRemote(originalCommand, messages, System.currentTimeMillis() - startedAt);
    }
}
