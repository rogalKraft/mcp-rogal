package org.wallet.rogalik.mcp.mixin.client;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private GLFW callback targets on {@link MouseHandler}.
 *
 * <p>Going through these rather than poking widgets directly means the whole vanilla path runs:
 * hover state, focus, click sounds and any screen-specific handling.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Invoker("onButton")
    void mcp$onButton(long window, MouseButtonInfo button, int action);

    @Invoker("onMove")
    void mcp$onMove(long window, double x, double y);

    @Invoker("onScroll")
    void mcp$onScroll(long window, double deltaX, double deltaY);
}
