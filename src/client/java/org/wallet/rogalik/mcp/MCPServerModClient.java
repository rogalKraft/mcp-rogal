package org.wallet.rogalik.mcp;

import net.fabricmc.api.ClientModInitializer;
import org.wallet.rogalik.mcp.bridge.HTTPMCPServer;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.tools.ToolRegistry;
import org.wallet.rogalik.mcp.utils.ClientTickScheduler;
import org.wallet.rogalik.mcp.utils.PauseGuard;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class MCPServerModClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("mcp-rogal");
	private HTTPMCPServer httpServer;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Minecraft MCP Client");

		try {
			MCPConfig config = MCPConfig.load();
			// Attach before anything else so the buffer catches startup errors too.
			org.wallet.rogalik.mcp.logs.McpLogs.install(config);

			// Drives every deferred client-side action: screenshot capture, key release, tick
			// waits. Options are not loaded yet here, so pause suppression rides along on the
			// same tick and applies itself on the first tick that has them.
			ClientTickEvents.END_CLIENT_TICK.register(client -> {
				ClientTickScheduler.onEndTick(client);
				PauseGuard.apply(client, config);
			});

			if (config.getServer().isAutoStart()) {
				String transport = config.getServer().getTransport();

				if ("http".equals(transport)) {
					httpServer = new HTTPMCPServer(config, ClientToolset.build(config));
					httpServer.start();
				} else {
					LOGGER.warn("Unsupported transport: {}. Only 'http' is supported.", transport);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to start MCP Server", e);
		}
	}

	public void onClientShutdown() {
		if (httpServer != null) {
			httpServer.stop();
			LOGGER.info("HTTP MCP Server stopped");
		}
	}

	/** Everything the client exposes; kept apart so the entry point stays readable as tools pile up. */
	static final class ClientToolset {
		private ClientToolset() {
		}

		static ToolRegistry build(MCPConfig config) {
			// In singleplayer the client hosts the server, so commands and tick waits go
			// through the real dispatcher rather than a fire-and-forget packet.
			Supplier<MinecraftServer> server = () -> Minecraft.getInstance().getSingleplayerServer();

			return new ToolRegistry()
				.register(new org.wallet.rogalik.mcp.server.tools.ExecuteCommandsTool(
					new org.wallet.rogalik.mcp.command.ClientCommandExecutor(config), config))
				.register(new org.wallet.rogalik.mcp.server.tools.GetPlayerInfoTool(
					new org.wallet.rogalik.mcp.utils.PlayerInfoProvider()))
				.register(new org.wallet.rogalik.mcp.server.tools.GetBlocksInAreaTool(
					new org.wallet.rogalik.mcp.utils.BlockScanner(), config))
				.register(new org.wallet.rogalik.mcp.server.tools.TakeScreenshotTool(
					new org.wallet.rogalik.mcp.utils.ScreenshotUtils(), config))
				.register(new org.wallet.rogalik.mcp.server.tools.AdvanceTicksTool(config, server))
				.register(new org.wallet.rogalik.mcp.server.tools.GetLogsTool())
				.register(new org.wallet.rogalik.mcp.server.tools.WaitForLogTool(config))
				.register(new org.wallet.rogalik.mcp.server.tools.GetCrashReportsTool())
				// Authoring aids: valid ids, block-state syntax, command checking, files.
				.register(new org.wallet.rogalik.mcp.server.tools.ListIdsTool())
				.register(new org.wallet.rogalik.mcp.server.tools.DescribeBlockStateTool())
				.register(new org.wallet.rogalik.mcp.server.tools.ValidateCommandTool(server))
				.register(new org.wallet.rogalik.mcp.server.tools.FilesTool(config))
				.register(new org.wallet.rogalik.mcp.server.tools.WorldInfoTool(server))
				.register(new org.wallet.rogalik.mcp.server.tools.ReloadDatapacksTool(config, server))
				.register(new org.wallet.rogalik.mcp.server.tools.FakePlayersTool(config, server))
				// Input and interface control, client only by nature.
				.register(new org.wallet.rogalik.mcp.input.PressKeyTool(config))
				.register(new org.wallet.rogalik.mcp.input.TypeTextTool(config))
				.register(new org.wallet.rogalik.mcp.input.SendKeybindTool(config))
				.register(new org.wallet.rogalik.mcp.input.ListKeybindsTool())
				.register(new org.wallet.rogalik.mcp.input.MouseTool(config))
				.register(new org.wallet.rogalik.mcp.ui.GetOpenScreenTool())
				.register(new org.wallet.rogalik.mcp.ui.ClickWidgetTool(config))
				.register(new org.wallet.rogalik.mcp.ui.OpenScreenTool(config))
				.register(new org.wallet.rogalik.mcp.ui.OptionsTool(config))
				.register(new org.wallet.rogalik.mcp.ui.ReloadResourcesTool(config))
				.register(new org.wallet.rogalik.mcp.ui.RejoinWorldTool(config));
		}
	}
}
