package org.wallet.rogalik.mcp.server.fakeplayer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Puts dead fake players back in the world.
 *
 * <p>A real player leaves the death screen by clicking Respawn. A fake one has no client to click
 * it, so without this it would lie dead forever — and any rule that only fires on respawn, such as
 * handing a tracking compass back, could never be observed.
 */
public final class FakePlayerRespawner {

    private static final Logger LOGGER = LoggerFactory.getLogger(FakePlayerRespawner.class);

    /** Wait a moment after death so death-triggered logic runs before the respawn does. */
    private static final int RESPAWN_DELAY_TICKS = 20;

    private static volatile boolean enabled = true;

    private FakePlayerRespawner() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** Runs at the end of each server tick. */
    public static void tick(MinecraftServer server) {
        if (!enabled || server.getTickCount() % 10 != 0) {
            return;
        }

        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        for (ServerPlayer player : players) {
            if (!FakePlayerRegistry.isFake(player) || !player.isDeadOrDying()) {
                continue;
            }
            if (player.deathTime < RESPAWN_DELAY_TICKS) {
                continue;
            }

            try {
                ServerPlayer respawned = server.getPlayerList()
                    .respawn(player, false, Entity.RemovalReason.KILLED);
                // The player list hands back a new entity; keep it recognised as fake.
                FakePlayerRegistry.register(respawned.getUUID());
            } catch (Exception e) {
                LOGGER.warn("Could not respawn fake player {}", player.getName().getString(), e);
            }
        }
    }
}
