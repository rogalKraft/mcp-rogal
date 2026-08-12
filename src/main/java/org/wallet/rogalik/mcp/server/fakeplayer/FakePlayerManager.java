package org.wallet.rogalik.mcp.server.fakeplayer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Spawns, despawns and lists fake players, always on the server thread. */
public final class FakePlayerManager {

    private FakePlayerManager() {
    }

    /** Names are what commands and selectors address, so keep them to what the game accepts. */
    public static Optional<String> validateName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.of("A name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 16) {
            return Optional.of("'" + trimmed + "' is longer than the 16 character limit");
        }
        if (!trimmed.matches("[A-Za-z0-9_]+")) {
            return Optional.of("'" + trimmed + "' may only contain letters, digits and underscores");
        }
        return Optional.empty();
    }

    public static List<ServerPlayer> fakePlayers(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
            .filter(FakePlayerRegistry::isFake)
            .toList();
    }

    public static boolean isOnline(MinecraftServer server, String name) {
        return server.getPlayerList().getPlayerByName(name) != null;
    }

    public static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return server.overworld();
        }
        String wanted = dimensionId.trim().toLowerCase(Locale.ROOT);
        String qualified = wanted.contains(":") ? wanted : "minecraft:" + wanted;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(qualified)) {
                return level;
            }
        }
        return null;
    }

    public static GameType resolveGameMode(String gameMode, GameType fallback) {
        if (gameMode == null || gameMode.isBlank()) {
            return fallback;
        }
        try {
            return GameType.valueOf(gameMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Where to put a fake player when the caller did not say: next to the only real player. */
    public static Vec3 defaultPosition(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!FakePlayerRegistry.isFake(player)) {
                return player.position();
            }
        }
        var respawn = server.overworld().getRespawnData();
        var spawn = respawn == null ? null : respawn.pos();
        if (spawn == null) {
            return new Vec3(0.5, 64, 0.5);
        }
        return new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
    }

    public static FakePlayer spawn(
        MinecraftServer server,
        String name,
        ServerLevel level,
        Vec3 position,
        float yaw,
        float pitch,
        GameType gameMode,
        long timeoutMs
    ) throws Exception {
        FakePlayer player = server.submit(() -> FakePlayer.spawn(
            server, level, name, position.x, position.y, position.z, yaw, pitch, gameMode))
            .get(timeoutMs, TimeUnit.MILLISECONDS);
        FakePlayerRegistry.register(player.getUUID());
        return player;
    }

    /**
     * Removes a fake player through the normal disconnect path.
     *
     * <p>Going through {@code PlayerList.remove} rather than discarding the entity is what makes
     * a leave event fire, which is the only way to exercise grace periods and other
     * disconnect-driven behaviour.
     */
    public static boolean despawn(MinecraftServer server, String name, long timeoutMs) throws Exception {
        return server.submit(() -> {
            ServerPlayer player = server.getPlayerList().getPlayerByName(name);
            if (player == null || !FakePlayerRegistry.isFake(player)) {
                return false;
            }
            FakePlayerRegistry.unregister(player.getUUID());
            server.getPlayerList().remove(player);
            return true;
        }).get(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
