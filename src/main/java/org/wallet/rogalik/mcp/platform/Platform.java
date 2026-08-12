package org.wallet.rogalik.mcp.platform;

import java.nio.file.Path;
import java.util.List;

/**
 * Everything the mod needs from the mod loader, in one place.
 *
 * <p>All other code talks to the loader through this interface, so porting to another loader
 * means writing one new implementation and swapping the entry points — the rest of the mod is
 * plain Minecraft API and moves across unchanged.
 */
public interface Platform {

    Path gameDir();

    Path configDir();

    /** True when running on a physical client (including a client hosting a singleplayer world). */
    boolean isClient();

    List<ModInfo> loadedMods();

    record ModInfo(String id, String name, String version) {
    }

    static Platform get() {
        Platform platform = Holder.instance;
        if (platform == null) {
            throw new IllegalStateException(
                "Platform has not been initialised yet - call Platform.set() from the mod entry point");
        }
        return platform;
    }

    static void set(Platform platform) {
        Holder.instance = platform;
    }

    /** Holds the active implementation; interfaces cannot have mutable static fields directly. */
    final class Holder {
        private static volatile Platform instance;

        private Holder() {
        }
    }
}
