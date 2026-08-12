package org.wallet.rogalik.mcp.utils;

import org.wallet.rogalik.mcp.config.MCPConfig;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stops the game pausing itself when its window loses focus.
 *
 * <p>Singleplayer normally pauses the moment you click away, and the pause menu covers the view.
 * That makes every screenshot and every screen inspection report the pause menu instead of what
 * the game was actually showing, so working in another window while the mod drives Minecraft
 * would silently produce useless results. With this on, only Escape pauses.
 */
public final class PauseGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(PauseGuard.class);

    private static boolean applied = false;

    private PauseGuard() {
    }

    /**
     * Applies the setting once options are available.
     *
     * <p>Called from the client tick because {@code Minecraft.options} is not populated yet when
     * the client initialiser runs.
     */
    public static void apply(Minecraft client, MCPConfig config) {
        if (applied || client == null || client.options == null) {
            return;
        }
        applied = true;

        if (!config.getInput().isPreventPauseOnLostFocus()) {
            return;
        }
        if (!client.options.pauseOnLostFocus) {
            return;
        }

        client.options.pauseOnLostFocus = false;
        LOGGER.info("Disabled pause-on-lost-focus so the view stays usable while the MCP server "
            + "drives the game; Escape still pauses. Set input.preventPauseOnLostFocus to false "
            + "in mcp.json to keep vanilla behaviour.");
    }

    /** Test hook so the one-shot can be exercised more than once. */
    static void resetForTesting() {
        applied = false;
    }
}
