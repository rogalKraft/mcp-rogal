package org.wallet.rogalik.mcp.mixin.client;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private window-callback targets on {@link MouseHandler} (GLFW callbacks before
 * Minecraft 26.3, SDL3 since).
 *
 * <p>Going through these rather than poking widgets directly means the whole vanilla path runs:
 * hover state, focus, click sounds and any screen-specific handling.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Invoker("onButton")
    void mcp$onButton(long window, MouseButtonInfo button, int action);

    /**
     * 26.3 added two trailing params carrying SDL's own relative-motion deltas alongside the
     * absolute position (GLFW only ever gave an absolute x/y and Minecraft computed its own
     * delta from the previous call). Synthetic moves from this mod are absolute placements, not
     * physical mouse motion, so there's no real delta to report - 0,0 is passed, same as GLFW-era
     * code effectively got when a move landed exactly on the previous position.
     */
    @Invoker("onMove")
    void mcp$onMove(long window, double x, double y, double deltaX, double deltaY);

    @Invoker("onScroll")
    void mcp$onScroll(long window, double deltaX, double deltaY);
}
