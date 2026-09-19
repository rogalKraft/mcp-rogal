package org.wallet.rogalik.mcp.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Translates human-friendly key and button names into key codes.
 *
 * <p>Built on {@code InputConstants} rather than raw platform codes: the constants inline at
 * compile time, so this class stays pure and can be unit tested without a window, a GL context or
 * an initialised game. Minecraft 26.3 replaced GLFW with SDL3 for windowing and input, so these
 * used to be {@code GLFW_KEY_*}; a handful of numpad keys (subtract/divide/decimal) aren't exposed
 * by {@code InputConstants} at all and fall back to the raw {@code SDLScancode} they wrap.
 */
public final class KeyCodes {

    private static final Map<String, Integer> KEYS_BY_NAME = new LinkedHashMap<>();
    private static final Map<Integer, String> NAMES_BY_KEY = new TreeMap<>();
    private static final Map<String, Integer> MOUSE_BUTTONS = new LinkedHashMap<>();

    /** Modifier name to the modifier bit reported alongside an event. */
    private static final Map<String, Integer> MODIFIER_MASKS = Map.of(
        "shift", InputConstants.MOD_SHIFT,
        "ctrl", InputConstants.MOD_CONTROL,
        "control", InputConstants.MOD_CONTROL,
        "alt", InputConstants.MOD_ALT,
        "super", InputConstants.MOD_SUPER,
        "cmd", InputConstants.MOD_SUPER,
        "meta", InputConstants.MOD_SUPER
    );

    /** Modifier name to the physical key that must be held for the game to see it pressed. */
    private static final Map<String, Integer> MODIFIER_KEYS = Map.of(
        "shift", InputConstants.KEY_LSHIFT,
        "ctrl", InputConstants.KEY_LCONTROL,
        "control", InputConstants.KEY_LCONTROL,
        "alt", InputConstants.KEY_LALT,
        "super", InputConstants.KEY_LGUI,
        "cmd", InputConstants.KEY_LGUI,
        "meta", InputConstants.KEY_LGUI
    );

    static {
        for (char c = 'a'; c <= 'z'; c++) {
            put(String.valueOf(c), InputConstants.KEY_A + (c - 'a'));
        }
        // Digits aren't offset-from-KEY_0 under SDL's scancode layout the way they were under
        // GLFW - scancode 0 sits after 9, not before 1 - so each one is named explicitly instead
        // of computed. Same story for the numpad row just below.
        put("0", InputConstants.KEY_0);
        put("1", InputConstants.KEY_1);
        put("2", InputConstants.KEY_2);
        put("3", InputConstants.KEY_3);
        put("4", InputConstants.KEY_4);
        put("5", InputConstants.KEY_5);
        put("6", InputConstants.KEY_6);
        put("7", InputConstants.KEY_7);
        put("8", InputConstants.KEY_8);
        put("9", InputConstants.KEY_9);

        // F1-F12 are sequential from KEY_F1, but F13-F24 are a separate block under SDL, not a
        // continuation - the same "not actually contiguous" trap as the digits, caught the same
        // way, by an explicit F13 anchor instead of extending the F1 loop past F12.
        for (int i = 1; i <= 12; i++) {
            put("f" + i, InputConstants.KEY_F1 + (i - 1));
        }
        for (int i = 13; i <= 24; i++) {
            put("f" + i, InputConstants.KEY_F13 + (i - 13));
        }
        // F25 existed under GLFW but SDL's scancode set stops at F24 - no key to map it to.

        put("numpad0", InputConstants.KEY_NUMPAD0);
        put("numpad1", InputConstants.KEY_NUMPAD1);
        put("numpad2", InputConstants.KEY_NUMPAD2);
        put("numpad3", InputConstants.KEY_NUMPAD3);
        put("numpad4", InputConstants.KEY_NUMPAD4);
        put("numpad5", InputConstants.KEY_NUMPAD5);
        put("numpad6", InputConstants.KEY_NUMPAD6);
        put("numpad7", InputConstants.KEY_NUMPAD7);
        put("numpad8", InputConstants.KEY_NUMPAD8);
        put("numpad9", InputConstants.KEY_NUMPAD9);

        put("escape", InputConstants.KEY_ESCAPE);
        put("esc", InputConstants.KEY_ESCAPE);
        put("enter", InputConstants.KEY_RETURN);
        put("return", InputConstants.KEY_RETURN);
        put("tab", InputConstants.KEY_TAB);
        put("backspace", InputConstants.KEY_BACKSPACE);
        put("insert", InputConstants.KEY_INSERT);
        put("delete", InputConstants.KEY_DELETE);
        put("space", InputConstants.KEY_SPACE);

        put("right", InputConstants.KEY_RIGHT);
        put("left", InputConstants.KEY_LEFT);
        put("down", InputConstants.KEY_DOWN);
        put("up", InputConstants.KEY_UP);
        put("pageup", InputConstants.KEY_PAGEUP);
        put("pagedown", InputConstants.KEY_PAGEDOWN);
        put("home", InputConstants.KEY_HOME);
        put("end", InputConstants.KEY_END);

        put("capslock", InputConstants.KEY_CAPSLOCK);
        put("scrolllock", InputConstants.KEY_SCROLLLOCK);
        put("numlock", InputConstants.KEY_NUMLOCK);
        put("printscreen", InputConstants.KEY_PRINTSCREEN);
        put("pause", InputConstants.KEY_PAUSE);

        put("lshift", InputConstants.KEY_LSHIFT);
        put("rshift", InputConstants.KEY_RSHIFT);
        put("lcontrol", InputConstants.KEY_LCONTROL);
        put("lctrl", InputConstants.KEY_LCONTROL);
        put("rcontrol", InputConstants.KEY_RCONTROL);
        put("rctrl", InputConstants.KEY_RCONTROL);
        put("lalt", InputConstants.KEY_LALT);
        put("ralt", InputConstants.KEY_RALT);
        put("lsuper", InputConstants.KEY_LGUI);
        put("rsuper", InputConstants.KEY_RGUI);

        // Minecraft spells these out as key.keyboard.left.shift, which normalises to "leftshift".
        put("leftshift", InputConstants.KEY_LSHIFT);
        put("rightshift", InputConstants.KEY_RSHIFT);
        put("leftcontrol", InputConstants.KEY_LCONTROL);
        put("rightcontrol", InputConstants.KEY_RCONTROL);
        put("leftalt", InputConstants.KEY_LALT);
        put("rightalt", InputConstants.KEY_RALT);
        put("leftsuper", InputConstants.KEY_LGUI);
        put("rightsuper", InputConstants.KEY_RGUI);
        put("leftwin", InputConstants.KEY_LGUI);
        put("rightwin", InputConstants.KEY_RGUI);
        put("keypadenter", InputConstants.KEY_NUMPADENTER);

        put("apostrophe", InputConstants.KEY_APOSTROPHE);
        put("comma", InputConstants.KEY_COMMA);
        put("minus", InputConstants.KEY_MINUS);
        put("period", InputConstants.KEY_PERIOD);
        put("slash", InputConstants.KEY_SLASH);
        put("semicolon", InputConstants.KEY_SEMICOLON);
        put("equal", InputConstants.KEY_EQUALS);
        put("leftbracket", InputConstants.KEY_LBRACKET);
        put("backslash", InputConstants.KEY_BACKSLASH);
        put("rightbracket", InputConstants.KEY_RBRACKET);
        put("grave", InputConstants.KEY_GRAVE);

        put("numpadadd", InputConstants.KEY_ADD);
        put("numpadsubtract", SDLScancode.SDL_SCANCODE_KP_MINUS);
        put("numpadmultiply", InputConstants.KEY_MULTIPLY);
        put("numpaddivide", SDLScancode.SDL_SCANCODE_KP_DIVIDE);
        put("numpaddecimal", SDLScancode.SDL_SCANCODE_KP_DECIMAL);
        put("numpadenter", InputConstants.KEY_NUMPADENTER);
        put("numpadequal", InputConstants.KEY_NUMPADEQUALS);

        MOUSE_BUTTONS.put("left", InputConstants.MOUSE_BUTTON_LEFT);
        MOUSE_BUTTONS.put("right", InputConstants.MOUSE_BUTTON_RIGHT);
        MOUSE_BUTTONS.put("middle", InputConstants.MOUSE_BUTTON_MIDDLE);
        MOUSE_BUTTONS.put("button4", InputConstants.MOUSE_BUTTON_4);
        MOUSE_BUTTONS.put("button5", InputConstants.MOUSE_BUTTON_5);
        MOUSE_BUTTONS.put("button6", InputConstants.MOUSE_BUTTON_6);
        MOUSE_BUTTONS.put("button7", InputConstants.MOUSE_BUTTON_7);
        MOUSE_BUTTONS.put("button8", InputConstants.MOUSE_BUTTON_8);
    }

    private KeyCodes() {
    }

    private static void put(String name, int code) {
        KEYS_BY_NAME.put(name, code);
        NAMES_BY_KEY.putIfAbsent(code, name);
    }

    /**
     * Resolves a key name.
     *
     * <p>Accepts friendly names ({@code "escape"}, {@code "f3"}, {@code "a"}), Minecraft's own
     * translation keys ({@code "key.keyboard.left.shift"}) and raw numeric GLFW codes.
     */
    public static OptionalInt keyByName(String name) {
        if (name == null) {
            return OptionalInt.empty();
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return OptionalInt.empty();
        }

        if (normalized.startsWith("key.keyboard.")) {
            normalized = normalized.substring("key.keyboard.".length()).replace(".", "");
        }
        normalized = normalized.replace("_", "").replace("-", "").replace(" ", "");

        Integer direct = KEYS_BY_NAME.get(normalized);
        if (direct != null) {
            return OptionalInt.of(direct);
        }

        // A bare number is ambiguous: "5" is the digit key, but a caller may also pass a raw
        // GLFW code. Digits are handled above, so anything left that parses is treated as a code.
        try {
            return OptionalInt.of(Integer.parseInt(normalized));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    public static String nameOf(int keyCode) {
        return NAMES_BY_KEY.getOrDefault(keyCode, "key_" + keyCode);
    }

    public static OptionalInt mouseButtonByName(String name) {
        if (name == null) {
            return OptionalInt.of(InputConstants.MOUSE_BUTTON_LEFT);
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return OptionalInt.of(InputConstants.MOUSE_BUTTON_LEFT);
        }

        Integer direct = MOUSE_BUTTONS.get(normalized);
        if (direct != null) {
            return OptionalInt.of(direct);
        }
        try {
            int parsed = Integer.parseInt(normalized);
            return parsed >= 0 && parsed <= 7 ? OptionalInt.of(parsed) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /** Combines modifier names into the bitmask the game receives with an event. */
    public static int modifierMask(List<String> modifiers) {
        if (modifiers == null) {
            return 0;
        }
        int mask = 0;
        for (String modifier : modifiers) {
            if (modifier == null) {
                continue;
            }
            Integer bit = MODIFIER_MASKS.get(modifier.trim().toLowerCase(Locale.ROOT));
            if (bit != null) {
                mask |= bit;
            }
        }
        return mask;
    }

    /** Names in {@code modifiers} that are not recognised, so a typo can be reported. */
    public static List<String> unknownModifiers(List<String> modifiers) {
        if (modifiers == null) {
            return List.of();
        }
        return modifiers.stream()
            .filter(m -> m != null && !MODIFIER_MASKS.containsKey(m.trim().toLowerCase(Locale.ROOT)))
            .toList();
    }

    /** The physical keys to hold down for the given modifiers, in press order. */
    public static List<Integer> modifierKeyCodes(List<String> modifiers) {
        if (modifiers == null) {
            return List.of();
        }
        return modifiers.stream()
            .filter(m -> m != null)
            .map(m -> MODIFIER_KEYS.get(m.trim().toLowerCase(Locale.ROOT)))
            .filter(code -> code != null)
            .distinct()
            .toList();
    }

    public static List<String> knownKeyNames() {
        return List.copyOf(KEYS_BY_NAME.keySet());
    }

    public static List<String> knownMouseButtons() {
        return List.copyOf(MOUSE_BUTTONS.keySet());
    }
}
