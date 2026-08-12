package org.wallet.rogalik.mcp.mixin.client;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the level storage handle so the save folder name can be read back.
 *
 * <p>Reopening a world needs its directory id, and the world data only carries the display name,
 * which is not the same thing once a world has been renamed or duplicated.
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess mcp$storageSource();
}
