package org.wallet.rogalik.mcp.platform;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FabricPlatform implements Platform {

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public List<ModInfo> loadedMods() {
        List<ModInfo> mods = new ArrayList<>();
        FabricLoader.getInstance().getAllMods().forEach(container -> {
            var metadata = container.getMetadata();
            mods.add(new ModInfo(metadata.getId(), metadata.getName(), metadata.getVersion().getFriendlyString()));
        });
        return mods;
    }
}
