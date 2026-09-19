package org.wallet.rogalik.mcp.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

public class KeyCodesTest {

    @Test
    public void lettersAndDigitsResolve() {
        assertEquals(InputConstants.KEY_A, KeyCodes.keyByName("a").getAsInt());
        assertEquals(InputConstants.KEY_Z, KeyCodes.keyByName("z").getAsInt());
        assertEquals(InputConstants.KEY_0, KeyCodes.keyByName("0").getAsInt());
        assertEquals(InputConstants.KEY_9, KeyCodes.keyByName("9").getAsInt());
    }

    @Test
    public void lookupIsCaseInsensitiveAndTrimmed() {
        assertEquals(InputConstants.KEY_ESCAPE, KeyCodes.keyByName("  ESCAPE ").getAsInt());
        assertEquals(InputConstants.KEY_F3, KeyCodes.keyByName("F3").getAsInt());
    }

    @Test
    public void commonAliasesResolveToTheSameKey() {
        assertEquals(KeyCodes.keyByName("escape").getAsInt(), KeyCodes.keyByName("esc").getAsInt());
        assertEquals(KeyCodes.keyByName("enter").getAsInt(), KeyCodes.keyByName("return").getAsInt());
        assertEquals(KeyCodes.keyByName("lcontrol").getAsInt(), KeyCodes.keyByName("lctrl").getAsInt());
    }

    @Test
    public void minecraftTranslationKeysAreAccepted() {
        assertEquals(InputConstants.KEY_A, KeyCodes.keyByName("key.keyboard.a").getAsInt());
        assertEquals(InputConstants.KEY_LSHIFT, KeyCodes.keyByName("key.keyboard.left.shift").getAsInt());
        assertEquals(InputConstants.KEY_PAGEUP, KeyCodes.keyByName("key.keyboard.page.up").getAsInt());
    }

    @Test
    public void separatorsInNamesAreIgnored() {
        assertEquals(InputConstants.KEY_PAGEDOWN, KeyCodes.keyByName("page_down").getAsInt());
        assertEquals(InputConstants.KEY_CAPSLOCK, KeyCodes.keyByName("caps-lock").getAsInt());
    }

    @Test
    public void functionAndNumpadKeysCoverTheirFullRange() {
        // F25 doesn't exist any more - Minecraft 26.3 moved from GLFW to SDL3, and SDL's own
        // scancode set stops at F24. f1 and numpad0/9 are still there under their SDL-backed names.
        assertEquals(InputConstants.KEY_F1, KeyCodes.keyByName("f1").getAsInt());
        assertEquals(InputConstants.KEY_F24, KeyCodes.keyByName("f24").getAsInt());
        assertEquals(OptionalInt.empty(), KeyCodes.keyByName("f25"));
        assertEquals(InputConstants.KEY_NUMPAD0, KeyCodes.keyByName("numpad0").getAsInt());
        assertEquals(InputConstants.KEY_NUMPAD9, KeyCodes.keyByName("numpad9").getAsInt());
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
        assertEquals(InputConstants.KEY_ESCAPE, KeyCodes.keyByName(String.valueOf(InputConstants.KEY_ESCAPE)).getAsInt());
    }

    @Test
    public void nameOfRoundTripsKnownKeys() {
        assertEquals("escape", KeyCodes.nameOf(InputConstants.KEY_ESCAPE));
        assertEquals("a", KeyCodes.nameOf(InputConstants.KEY_A));
        assertTrue(KeyCodes.nameOf(-999).startsWith("key_"), "Unknown codes get a readable placeholder");
    }

    @Test
    public void mouseButtonsResolveByNameAndDefaultToLeft() {
        assertEquals(InputConstants.MOUSE_BUTTON_LEFT, KeyCodes.mouseButtonByName("left").getAsInt());
        assertEquals(InputConstants.MOUSE_BUTTON_RIGHT, KeyCodes.mouseButtonByName("right").getAsInt());
        assertEquals(InputConstants.MOUSE_BUTTON_MIDDLE, KeyCodes.mouseButtonByName("middle").getAsInt());
        assertEquals(InputConstants.MOUSE_BUTTON_LEFT, KeyCodes.mouseButtonByName(null).getAsInt());
        assertEquals(OptionalInt.empty(), KeyCodes.mouseButtonByName("elbow"));
    }

    @Test
    public void modifierMaskCombinesBits() {
        int mask = KeyCodes.modifierMask(List.of("ctrl", "shift"));

        assertEquals(InputConstants.MOD_CONTROL | InputConstants.MOD_SHIFT, mask);
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
        assertTrue(codes.contains(InputConstants.KEY_LCONTROL));
        assertTrue(codes.contains(InputConstants.KEY_LSHIFT));
    }

    @Test
    public void unknownModifiersContributeNoKeyCodes() {
        assertEquals(List.of(InputConstants.KEY_LALT), KeyCodes.modifierKeyCodes(List.of("alt", "hyper")));
    }
}
