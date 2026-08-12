package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.utils.IPlayerInfoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetPlayerInfoTool implements McpTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetPlayerInfoTool.class);

    private final IPlayerInfoProvider provider;

    public GetPlayerInfoTool(IPlayerInfoProvider provider) {
        this.provider = provider;
    }

    @Override
    public String name() {
        return "get_player_info";
    }

    @Override
    public String description() {
        return "Get a player's position and world context. CALL THIS FIRST before any building task "
            + "to get absolute coordinates.\n\n"
            + "Returns:\n"
            + "- blockPosition: {x,y,z} integer coordinates - USE THESE for setblock/fill commands\n"
            + "- position: {x,y,z} exact floating-point coordinates\n"
            + "- facingDirection: cardinal direction (North/South/East/West)\n"
            + "- frontPosition: {x,y,z} 3 blocks ahead of the player - a good starting point for builds\n"
            + "- gameMode, dimension, timeOfDay, health, foodLevel, permissionLevel, inventory\n\n"
            + "IMPORTANT: the Y axis is vertical (Y=64 is typical ground level). Build at frontPosition "
            + "or offset from blockPosition using absolute coordinates for reliability.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .string("player",
                "Name of the player to inspect. Defaults to the local player on a client, or the "
                    + "first player online on a dedicated server", false)
            .bool("include_inventory", "Include the full inventory with item components", false, true)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        try {
            JsonObject playerInfo = provider.getPlayerInfo(arguments);
            if (playerInfo.has("error")) {
                return MCPProtocol.createErrorResponse(playerInfo.get("error").getAsString(), null);
            }
            return MCPProtocol.createSuccessResponse(playerInfo.toString());
        } catch (Exception e) {
            LOGGER.error("Error getting player info", e);
            return MCPProtocol.createErrorResponse("Failed to get player information: " + e.getMessage(), null);
        }
    }
}
