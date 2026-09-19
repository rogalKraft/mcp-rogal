package org.wallet.rogalik.mcp.mixin.client;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private window-callback targets on {@link KeyboardHandler} (GLFW callbacks before
 * Minecraft 26.3, SDL3 since - the signatures here didn't change between the two).
 *
 * <p>Driving these is what makes simulated input behave like real input: the same method the
 * window callback calls also feeds the open screen, text fields and key bindings. Setting
 * {@code KeyMapping} state directly would only work in the world and would be invisible to menus.
 */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerAccessor {

    @Invoker("keyPress")
    void mcp$keyPress(long window, int action, KeyEvent event);

    @Invoker("charTyped")
    void mcp$charTyped(long window, CharacterEvent event);
}
