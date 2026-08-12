package org.wallet.rogalik.mcp.input;

import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Translates human-friendly key and button names into GLFW codes.
 *
 * <p>Deliberately built on {@code GLFW} integer constants rather than {@code InputConstants}: the
 * constants inline at compile time, so this class stays pure and can be unit tested without a
 * window, a GL context or an initialised game.
 */
public final class KeyCodes {

    private static final Map<String, Integer> KEYS_BY_NAME = new LinkedHashMap<>();
    private static final Map<Integer, String> NAMES_BY_KEY = new TreeMap<>();
    private static final Map<String, Integer> MOUSE_BUTTONS = new LinkedHashMap<>();

    /** Modifier name to the GLFW modifier bit reported alongside an event. */
    private static final Map<String, Integer> MODIFIER_MASKS = Map.of(
        "shift", GLFW.GLFW_MOD_SHIFT,
        "ctrl", GLFW.GLFW_MOD_CONTROL,
        "control", GLFW.GLFW_MOD_CONTROL,
        "alt", GLFW.GLFW_MOD_ALT,
        "super", GLFW.GLFW_MOD_SUPER,
        "cmd", GLFW.GLFW_MOD_SUPER,
        "meta", GLFW.GLFW_MOD_SUPER
    );

    /** Modifier name to the physical key that must be held for the game to see it pressed. */
    private static final Map<String, Integer> MODIFIER_KEYS = Map.of(
        "shift", GLFW.GLFW_KEY_LEFT_SHIFT,
        "ctrl", GLFW.GLFW_KEY_LEFT_CONTROL,
        "control", GLFW.GLFW_KEY_LEFT_CONTROL,
        "alt", GLFW.GLFW_KEY_LEFT_ALT,
        "super", GLFW.GLFW_KEY_LEFT_SUPER,
        "cmd", GLFW.GLFW_KEY_LEFT_SUPER,
        "meta", GLFW.GLFW_KEY_LEFT_SUPER
    );

    static {
        for (char c = 'a'; c <= 'z'; c++) {
            put(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'a'));
        }
        for (char c = '0'; c <= '9'; c++) {
            put(String.valueOf(c), GLFW.GLFW_KEY_0 + (c - '0'));
        }
        for (int i = 1; i <= 25; i++) {
            put("f" + i, GLFW.GLFW_KEY_F1 + (i - 1));
        }
        for (int i = 0; i <= 9; i++) {
            put("numpad" + i, GLFW.GLFW_KEY_KP_0 + i);
        }

        put("escape", GLFW.GLFW_KEY_ESCAPE);
        put("esc", GLFW.GLFW_KEY_ESCAPE);
        put("enter", GLFW.GLFW_KEY_ENTER);
        put("return", GLFW.GLFW_KEY_ENTER);
        put("tab", GLFW.GLFW_KEY_TAB);
        put("backspace", GLFW.GLFW_KEY_BACKSPACE);
        put("insert", GLFW.GLFW_KEY_INSERT);
        put("delete", GLFW.GLFW_KEY_DELETE);
        put("space", GLFW.GLFW_KEY_SPACE);

        put("right", GLFW.GLFW_KEY_RIGHT);
        put("left", GLFW.GLFW_KEY_LEFT);
        put("down", GLFW.GLFW_KEY_DOWN);
        put("up", GLFW.GLFW_KEY_UP);
        put("pageup", GLFW.GLFW_KEY_PAGE_UP);
        put("pagedown", GLFW.GLFW_KEY_PAGE_DOWN);
        put("home", GLFW.GLFW_KEY_HOME);
        put("end", GLFW.GLFW_KEY_END);

        put("capslock", GLFW.GLFW_KEY_CAPS_LOCK);
        put("scrolllock", GLFW.GLFW_KEY_SCROLL_LOCK);
        put("numlock", GLFW.GLFW_KEY_NUM_LOCK);
        put("printscreen", GLFW.GLFW_KEY_PRINT_SCREEN);
        put("pause", GLFW.GLFW_KEY_PAUSE);

        put("lshift", GLFW.GLFW_KEY_LEFT_SHIFT);
        put("rshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        put("lcontrol", GLFW.GLFW_KEY_LEFT_CONTROL);
        put("lctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        put("rcontrol", GLFW.GLFW_KEY_RIGHT_CONTROL);
        put("rctrl", GLFW.GLFW_KEY_RIGHT_CONTROL);
        put("lalt", GLFW.GLFW_KEY_LEFT_ALT);
        put("ralt", GLFW.GLFW_KEY_RIGHT_ALT);
        put("lsuper", GLFW.GLFW_KEY_LEFT_SUPER);
        put("rsuper", GLFW.GLFW_KEY_RIGHT_SUPER);

        // Minecraft spells these out as key.keyboard.left.shift, which normalises to "leftshift".
        put("leftshift", GLFW.GLFW_KEY_LEFT_SHIFT);
        put("rightshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        put("leftcontrol", GLFW.GLFW_KEY_LEFT_CONTROL);
        put("rightcontrol", GLFW.GLFW_KEY_RIGHT_CONTROL);
        put("leftalt", GLFW.GLFW_KEY_LEFT_ALT);
        put("rightalt", GLFW.GLFW_KEY_RIGHT_ALT);
        put("leftsuper", GLFW.GLFW_KEY_LEFT_SUPER);
        put("rightsuper", GLFW.GLFW_KEY_RIGHT_SUPER);
        put("leftwin", GLFW.GLFW_KEY_LEFT_SUPER);
        put("rightwin", GLFW.GLFW_KEY_RIGHT_SUPER);
        put("keypadenter", GLFW.GLFW_KEY_KP_ENTER);

        put("apostrophe", GLFW.GLFW_KEY_APOSTROPHE);
        put("comma", GLFW.GLFW_KEY_COMMA);
        put("minus", GLFW.GLFW_KEY_MINUS);
        put("period", GLFW.GLFW_KEY_PERIOD);
        put("slash", GLFW.GLFW_KEY_SLASH);
        put("semicolon", GLFW.GLFW_KEY_SEMICOLON);
        put("equal", GLFW.GLFW_KEY_EQUAL);
        put("leftbracket", GLFW.GLFW_KEY_LEFT_BRACKET);
        put("backslash", GLFW.GLFW_KEY_BACKSLASH);
        put("rightbracket", GLFW.GLFW_KEY_RIGHT_BRACKET);
        put("grave", GLFW.GLFW_KEY_GRAVE_ACCENT);

        put("numpadadd", GLFW.GLFW_KEY_KP_ADD);
        put("numpadsubtract", GLFW.GLFW_KEY_KP_SUBTRACT);
        put("numpadmultiply", GLFW.GLFW_KEY_KP_MULTIPLY);
        put("numpaddivide", GLFW.GLFW_KEY_KP_DIVIDE);
        put("numpaddecimal", GLFW.GLFW_KEY_KP_DECIMAL);
        put("numpadenter", GLFW.GLFW_KEY_KP_ENTER);
        put("numpadequal", GLFW.GLFW_KEY_KP_EQUAL);

        MOUSE_BUTTONS.put("left", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        MOUSE_BUTTONS.put("right", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        MOUSE_BUTTONS.put("middle", GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        for (int i = 4; i <= 8; i++) {
            MOUSE_BUTTONS.put("button" + i, i - 1);
        }
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
            return OptionalInt.of(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return OptionalInt.of(GLFW.GLFW_MOUSE_BUTTON_LEFT);
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
