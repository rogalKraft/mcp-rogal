package org.wallet.rogalik.mcp.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Finds key bindings by name and resolves what they are currently bound to. */
public final class KeybindLookup {

    private KeybindLookup() {
    }

    public static List<KeyMapping> all() {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null || client.options.keyMappings == null) {
            return List.of();
        }
        return List.of(client.options.keyMappings);
    }

    /**
     * Looks up a binding by its translation key.
     *
     * <p>Accepts both the full id ({@code key.inventory}) and the bare suffix ({@code inventory}),
     * since the prefix is noise for a caller.
     */
    public static Optional<KeyMapping> byName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = name.trim();
        String prefixed = wanted.startsWith("key.") ? wanted : "key." + wanted;

        return all().stream()
            .filter(mapping -> mapping.getName().equalsIgnoreCase(wanted)
                || mapping.getName().equalsIgnoreCase(prefixed))
            .findFirst();
    }

    /** The key a binding is currently assigned to, or empty when it is unbound. */
    public static Optional<InputConstants.Key> boundKey(KeyMapping mapping) {
        if (mapping.isUnbound()) {
            return Optional.empty();
        }
        try {
            return Optional.of(InputConstants.getKey(mapping.saveString()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Category id without the namespace, e.g. {@code movement}. */
    public static String categoryOf(KeyMapping mapping) {
        try {
            return mapping.getCategory().id().getPath().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
