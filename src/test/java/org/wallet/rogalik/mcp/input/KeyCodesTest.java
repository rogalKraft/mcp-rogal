package org.wallet.rogalik.mcp.input;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

public class KeyCodesTest {

    @Test
    public void lettersAndDigitsResolve() {
        assertEquals(GLFW.GLFW_KEY_A, KeyCodes.keyByName("a").getAsInt());
        assertEquals(GLFW.GLFW_KEY_Z, KeyCodes.keyByName("z").getAsInt());
        assertEquals(GLFW.GLFW_KEY_0, KeyCodes.keyByName("0").getAsInt());
        assertEquals(GLFW.GLFW_KEY_9, KeyCodes.keyByName("9").getAsInt());
    }

    @Test
    public void lookupIsCaseInsensitiveAndTrimmed() {
        assertEquals(GLFW.GLFW_KEY_ESCAPE, KeyCodes.keyByName("  ESCAPE ").getAsInt());
        assertEquals(GLFW.GLFW_KEY_F3, KeyCodes.keyByName("F3").getAsInt());
    }

    @Test
    public void commonAliasesResolveToTheSameKey() {
        assertEquals(KeyCodes.keyByName("escape").getAsInt(), KeyCodes.keyByName("esc").getAsInt());
        assertEquals(KeyCodes.keyByName("enter").getAsInt(), KeyCodes.keyByName("return").getAsInt());
        assertEquals(KeyCodes.keyByName("lcontrol").getAsInt(), KeyCodes.keyByName("lctrl").getAsInt());
    }

    @Test
    public void minecraftTranslationKeysAreAccepted() {
        assertEquals(GLFW.GLFW_KEY_A, KeyCodes.keyByName("key.keyboard.a").getAsInt());
        assertEquals(GLFW.GLFW_KEY_LEFT_SHIFT, KeyCodes.keyByName("key.keyboard.left.shift").getAsInt());
        assertEquals(GLFW.GLFW_KEY_PAGE_UP, KeyCodes.keyByName("key.keyboard.page.up").getAsInt());
    }

    @Test
    public void separatorsInNamesAreIgnored() {
        assertEquals(GLFW.GLFW_KEY_PAGE_DOWN, KeyCodes.keyByName("page_down").getAsInt());
        assertEquals(GLFW.GLFW_KEY_CAPS_LOCK, KeyCodes.keyByName("caps-lock").getAsInt());
    }

    @Test
    public void functionAndNumpadKeysCoverTheirFullRange() {
        assertEquals(GLFW.GLFW_KEY_F1, KeyCodes.keyByName("f1").getAsInt());
        assertEquals(GLFW.GLFW_KEY_F25, KeyCodes.keyByName("f25").getAsInt());
        assertEquals(GLFW.GLFW_KEY_KP_0, KeyCodes.keyByName("numpad0").getAsInt());
        assertEquals(GLFW.GLFW_KEY_KP_9, KeyCodes.keyByName("numpad9").getAsInt());
    }

    @Test
    public void unknownAndEmptyNamesAreRejected() {
        assertEquals(OptionalInt.empty(), KeyCodes.keyByName("banana"));
        assertEquals(OptionalInt.empty(), KeyCodes.keyByName(""));
        assertEquals(OptionalInt.empty(), KeyCodes.keyByName("   "));
        assertEquals(OptionalInt.empty(), KeyCodes.keyByName(null));
    }

    @Test
    public void rawNumericCodesPassThrough() {
        // Digits resolve to their key, so a raw code only applies to values that are not 0-9.
        assertEquals(GLFW.GLFW_KEY_ESCAPE, KeyCodes.keyByName(String.valueOf(GLFW.GLFW_KEY_ESCAPE)).getAsInt());
    }

    @Test
    public void nameOfRoundTripsKnownKeys() {
        assertEquals("escape", KeyCodes.nameOf(GLFW.GLFW_KEY_ESCAPE));
        assertEquals("a", KeyCodes.nameOf(GLFW.GLFW_KEY_A));
        assertTrue(KeyCodes.nameOf(-999).startsWith("key_"), "Unknown codes get a readable placeholder");
    }

    @Test
    public void mouseButtonsResolveByNameAndDefaultToLeft() {
        assertEquals(GLFW.GLFW_MOUSE_BUTTON_LEFT, KeyCodes.mouseButtonByName("left").getAsInt());
        assertEquals(GLFW.GLFW_MOUSE_BUTTON_RIGHT, KeyCodes.mouseButtonByName("right").getAsInt());
        assertEquals(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, KeyCodes.mouseButtonByName("middle").getAsInt());
        assertEquals(GLFW.GLFW_MOUSE_BUTTON_LEFT, KeyCodes.mouseButtonByName(null).getAsInt());
        assertEquals(OptionalInt.empty(), KeyCodes.mouseButtonByName("elbow"));
    }

    @Test
    public void modifierMaskCombinesBits() {
        int mask = KeyCodes.modifierMask(List.of("ctrl", "shift"));

        assertEquals(GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT, mask);
        assertEquals(0, KeyCodes.modifierMask(List.of()));
        assertEquals(0, KeyCodes.modifierMask(null));
    }

    @Test
    public void modifierAliasesAgree() {
        assertEquals(KeyCodes.modifierMask(List.of("ctrl")), KeyCodes.modifierMask(List.of("control")));
        assertEquals(KeyCodes.modifierMask(List.of("super")), KeyCodes.modifierMask(List.of("cmd")));
    }

    @Test
    public void unknownModifiersAreReportedRatherThanIgnoredSilently() {
        assertEquals(List.of("hyper"), KeyCodes.unknownModifiers(List.of("ctrl", "hyper")));
        assertTrue(KeyCodes.unknownModifiers(List.of("ctrl", "shift", "alt")).isEmpty());
    }

    @Test
    public void modifierKeyCodesAreDeduplicated() {
        // ctrl and control are the same physical key; pressing it twice would leave it stuck.
        List<Integer> codes = KeyCodes.modifierKeyCodes(List.of("ctrl", "control", "shift"));

        assertEquals(2, codes.size());
        assertTrue(codes.contains(GLFW.GLFW_KEY_LEFT_CONTROL));
        assertTrue(codes.contains(GLFW.GLFW_KEY_LEFT_SHIFT));
    }

    @Test
    public void unknownModifiersContributeNoKeyCodes() {
        assertEquals(List.of(GLFW.GLFW_KEY_LEFT_ALT), KeyCodes.modifierKeyCodes(List.of("alt", "hyper")));
    }
}
