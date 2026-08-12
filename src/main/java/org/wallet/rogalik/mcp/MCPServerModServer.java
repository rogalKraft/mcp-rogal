package org.wallet.rogalik.mcp;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.wallet.rogalik.mcp.bridge.HTTPMCPServer;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCPServerModServer implements DedicatedServerModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("mcp-rogal");
    private HTTPMCPServer httpServer;

    @Override
    public void onInitializeServer() {
        LOGGER.info("Initializing Minecraft MCP Dedicated Server");

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                MCPConfig config = MCPConfig.load();
                org.wallet.rogalik.mcp.logs.McpLogs.install(config);

                if (config.getServer().isAutoStart()) {
                    String transport = config.getServer().getTransport();

                    if ("http".equals(transport)) {
                        // take_screenshot is simply not registered here - there is no framebuffer
                        // on a dedicated server, so it never appears in tools/list.
                        ToolRegistry registry = new ToolRegistry()
                            .register(new org.wallet.rogalik.mcp.server.tools.ExecuteCommandsTool(
                                new org.wallet.rogalik.mcp.server.exec.CommandRunner(config, () -> server), config))
                            .register(new org.wallet.rogalik.mcp.server.tools.GetPlayerInfoTool(
                                new org.wallet.rogalik.mcp.server.tools.ServerPlayerInfoProvider(server)))
                            .register(new org.wallet.rogalik.mcp.server.tools.GetBlocksInAreaTool(
                                new org.wallet.rogalik.mcp.server.tools.ServerBlockScanner(server), config))
                            .register(new org.wallet.rogalik.mcp.server.tools.AdvanceTicksTool(config, () -> server))
                            .register(new org.wallet.rogalik.mcp.server.tools.GetLogsTool())
                            .register(new org.wallet.rogalik.mcp.server.tools.WaitForLogTool(config))
                            .register(new org.wallet.rogalik.mcp.server.tools.GetCrashReportsTool())
                            .register(new org.wallet.rogalik.mcp.server.tools.ListIdsTool())
                            .register(new org.wallet.rogalik.mcp.server.tools.DescribeBlockStateTool())
                            .register(new org.wallet.rogalik.mcp.server.tools.ValidateCommandTool(() -> server))
                            .register(new org.wallet.rogalik.mcp.server.tools.FilesTool(config))
                            .register(new org.wallet.rogalik.mcp.server.tools.WorldInfoTool(() -> server))
                            .register(new org.wallet.rogalik.mcp.server.tools.ReloadDatapacksTool(config, () -> server))
                            .register(new org.wallet.rogalik.mcp.server.tools.FakePlayersTool(config, () -> server));

                        httpServer = new HTTPMCPServer(config, registry);
                        httpServer.start();
                    } else {
                        LOGGER.warn("Unsupported transport: {}. Only 'http' is supported.", transport);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to start MCP Server", e);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (httpServer != null) {
                httpServer.stop();
                LOGGER.info("HTTP MCP Server stopped");
            }
        });
    }
}
