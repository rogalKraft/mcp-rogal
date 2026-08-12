package org.wallet.rogalik.mcp;

import org.wallet.rogalik.mcp.platform.FabricPlatform;
import org.wallet.rogalik.mcp.platform.Platform;
import org.wallet.rogalik.mcp.server.fakeplayer.FakePlayerRespawner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCPServerMod implements ModInitializer {
	public static final String MOD_ID = "mcp-rogal";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Runs before either the client or the dedicated server initialiser, so everything
		// downstream can rely on Platform being available.
		Platform.set(new FabricPlatform());

		// Fake players have no client to leave the death screen for them.
		ServerTickEvents.END_SERVER_TICK.register(FakePlayerRespawner::tick);

		LOGGER.info("MCP Server Mod initialized");
	}
}