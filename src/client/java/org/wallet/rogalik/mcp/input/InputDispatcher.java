package org.wallet.rogalik.mcp.input;

import org.wallet.rogalik.mcp.mixin.client.KeyboardHandlerAccessor;
import org.wallet.rogalik.mcp.mixin.client.MouseHandlerAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Feeds synthetic input through the same entry points the window callbacks use.
 *
 * <p>Everything runs on the client thread and passes the real window handle, because both
 * handlers ignore events for any other window.
 */
public final class InputDispatcher {

    private InputDispatcher() {
    }

    private static Minecraft client() {
        return Minecraft.getInstance();
    }

    private static long windowHandle() {
        return client().getWindow().handle();
    }

    /** Queues work on the client thread and completes once it has run. */
    public static CompletableFuture<Void> onClientThread(Runnable action) {
        Minecraft minecraft = client();
        CompletableFuture<Void> future = new CompletableFuture<>();
        minecraft.execute(() -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    // ---------------------------------------------------------------- keyboard

    public static void keyAction(int key, int modifiers, int action) {
        KeyEvent event = new KeyEvent(key, scancodeOf(key), modifiers);
        ((KeyboardHandlerAccessor) client().keyboardHandler).mcp$keyPress(windowHandle(), action, event);
    }

    public static void pressKey(int key, int modifiers) {
        keyAction(key, modifiers, InputConstants.PRESS);
    }

    public static void releaseKey(int key, int modifiers) {
        keyAction(key, modifiers, InputConstants.RELEASE);
    }

    /**
     * Presses and releases a key with modifiers held around it.
     *
     * <p>The modifier keys are pressed as real keys as well as being set in the event mask:
     * screens read the mask, but in-world key bindings look at whether the physical key is down.
     */
    public static void tapKeyWithModifiers(int key, List<Integer> modifierKeys, int modifierMask) {
        holdModifiers(modifierKeys, modifierMask);
        pressKey(key, modifierMask);
        releaseKey(key, modifierMask);
        releaseModifiers(modifierKeys, modifierMask);
    }

    public static void holdModifiers(List<Integer> modifierKeys, int modifierMask) {
        for (int modifierKey : modifierKeys) {
            pressKey(modifierKey, modifierMask);
        }
    }

    public static void releaseModifiers(List<Integer> modifierKeys, int modifierMask) {
        for (int i = modifierKeys.size() - 1; i >= 0; i--) {
            releaseKey(modifierKeys.get(i), modifierMask);
        }
    }

    /**
     * Types text as character input.
     *
     * <p>Key events alone never produce characters — the platform layer (SDL, formerly GLFW)
     * reports those through a separate callback — so a text field only sees typing that arrives
     * this way.
     */
    public static void typeText(String text) {
        KeyboardHandlerAccessor keyboard = (KeyboardHandlerAccessor) client().keyboardHandler;
        long window = windowHandle();
        text.codePoints().forEach(codePoint ->
            keyboard.mcp$charTyped(window, new CharacterEvent(codePoint)));
    }

    /**
     * The second, "shortcut" code {@link KeyEvent} carries alongside the physical key - a
     * layout-aware code the game uses to recognise things like Ctrl+C as copy regardless of
     * keyboard layout. {@code InputConstants} only exposes that mapping for a handful of common
     * shortcut keys (letters, arrows, a few others); everything else falls back to 0, same as the
     * scancode this replaced - it's advisory, and a zero is accepted everywhere it's read.
     */
    private static int scancodeOf(int key) {
        return 0;
    }

    // ------------------------------------------------------------------- mouse

    public static void moveMouse(double windowX, double windowY) {
        // 0,0 for the relative-delta pair SDL added in 26.3 - see MouseHandlerAccessor.
        ((MouseHandlerAccessor) client().mouseHandler).mcp$onMove(windowHandle(), windowX, windowY, 0, 0);
    }

    public static void mouseButton(int button, int modifiers, int action) {
        MouseButtonInfo info = new MouseButtonInfo(button, modifiers);
        ((MouseHandlerAccessor) client().mouseHandler).mcp$onButton(windowHandle(), info, action);
    }

    public static void clickMouse(int button, int modifiers) {
        mouseButton(button, modifiers, InputConstants.PRESS);
        mouseButton(button, modifiers, InputConstants.RELEASE);
    }

    public static void scroll(double deltaX, double deltaY) {
        ((MouseHandlerAccessor) client().mouseHandler).mcp$onScroll(windowHandle(), deltaX, deltaY);
    }

    // ------------------------------------------------------------- coordinates

    /**
     * Converts GUI-space coordinates — the ones widgets are laid out in, and the ones
     * get_open_screen reports — into raw window pixels, which is what the handlers expect.
     */
    public static double guiToWindowX(double guiX) {
        return guiX * client().getWindow().getGuiScale();
    }

    public static double guiToWindowY(double guiY) {
        return guiY * client().getWindow().getGuiScale();
    }

    public static double windowToGuiX(double windowX) {
        return windowX / client().getWindow().getGuiScale();
    }

    public static double windowToGuiY(double windowY) {
        return windowY / client().getWindow().getGuiScale();
    }
}
