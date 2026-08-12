package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.config.MCPConfig;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.utils.IBlockScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetBlocksInAreaTool implements McpTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetBlocksInAreaTool.class);

    private final IBlockScanner scanner;
    private final MCPConfig config;

    public GetBlocksInAreaTool(IBlockScanner scanner, MCPConfig config) {
        this.scanner = scanner;
        this.config = config;
    }

    @Override
    public String name() {
        return "get_blocks_in_area";
    }

    @Override
    public String description() {
        return "Scan and return all non-air blocks in a rectangular area. Use this to VERIFY builds "
            + "after construction.\n\n"
            + "Maximum " + maxAreaSize() + " blocks per axis. Air blocks are excluded. Returns compressed "
            + "block data grouped by type with regions (connected areas) and single blocks.\n\n"
            + "After building, scan the area to check that walls have no gaps, doors have both halves, "
            + "the roof is covered and windows are placed. Fix anything wrong with execute_commands.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .nested("from", SchemaBuilder.blockPosition(null), "Starting corner of the area to scan", true)
            .nested("to", SchemaBuilder.blockPosition(null), "Opposite corner of the area to scan", true)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        try {
            if (!arguments.has("from") || !arguments.has("to")) {
                return MCPProtocol.createErrorResponse(
                    "Missing required parameters: 'from' and 'to' positions", null);
            }

            JsonObject fromPos = arguments.getAsJsonObject("from");
            JsonObject toPos = arguments.getAsJsonObject("to");

            if (!hasCoordinates(fromPos) || !hasCoordinates(toPos)) {
                return MCPProtocol.createErrorResponse(
                    "Position objects must contain x, y, z coordinates", null);
            }

            JsonObject result = scanner.scanBlocksInArea(fromPos, toPos, maxAreaSize());
            if (result.has("error")) {
                return MCPProtocol.createErrorResponse(result.get("error").getAsString(), null);
            }
            return MCPProtocol.createSuccessResponse(result.toString());
        } catch (Exception e) {
            LOGGER.error("Error getting blocks in area", e);
            return MCPProtocol.createErrorResponse("Failed to get blocks in area: " + e.getMessage(), null);
        }
    }

    private static boolean hasCoordinates(JsonObject position) {
        return position.has("x") && position.has("y") && position.has("z");
    }

    private int maxAreaSize() {
        return config != null && config.getServer() != null ? config.getServer().getMaxAreaSize() : 48;
    }
}
