package org.wallet.rogalik.mcp.server.fakeplayer;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which players are fake, by id rather than by class.
 *
 * <p>Respawning replaces the entity with a fresh {@code ServerPlayer} built by the vanilla player
 * list, so a class check stops recognising a fake player the moment it dies once. The id is
 * stable across that, which is also why fake players use a name-derived offline UUID.
 */
public final class FakePlayerRegistry {

    private static final Set<UUID> FAKE_IDS = ConcurrentHashMap.newKeySet();

    private FakePlayerRegistry() {
    }

    public static void register(UUID id) {
        FAKE_IDS.add(id);
    }

    public static void unregister(UUID id) {
        FAKE_IDS.remove(id);
    }

    public static boolean isFake(ServerPlayer player) {
        return player instanceof FakePlayer || FAKE_IDS.contains(player.getUUID());
    }

    public static int count() {
        return FAKE_IDS.size();
    }

    /** Test hook. */
    static void clear() {
        FAKE_IDS.clear();
    }
}
