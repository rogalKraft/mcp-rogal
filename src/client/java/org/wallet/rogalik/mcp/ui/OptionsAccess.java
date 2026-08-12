package org.wallet.rogalik.mcp.ui;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads and writes game settings directly.
 *
 * <p>Driving a slider through the options UI depends on layout, GUI scale and scroll position;
 * setting the value is the same change without any of that. Every setting is an
 * {@link OptionInstance} field on {@link Options}, so one reflective pass covers all of them
 * and keeps working as the option list changes between versions.
 */
public final class OptionsAccess {

    private OptionsAccess() {
    }

    /** Every option field, keyed by its field name, in declaration order. */
    public static Map<String, OptionInstance<?>> all(Options options) {
        Map<String, OptionInstance<?>> found = new LinkedHashMap<>();
        for (Field field : Options.class.getDeclaredFields()) {
            if (!OptionInstance.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(options);
                if (value instanceof OptionInstance<?> instance) {
                    found.put(field.getName(), instance);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // A field that refuses access is simply not offered; the rest stay usable.
            }
        }
        return found;
    }

    public static OptionInstance<?> byName(Options options, String name) {
        return all(options).get(name);
    }

    /**
     * Coerces a JSON-supplied value to the type the option already holds.
     *
     * @throws IllegalArgumentException when the value cannot represent that type
     */
    @SuppressWarnings("unchecked")
    public static void set(OptionInstance<?> option, com.google.gson.JsonElement value) {
        Object current = option.get();

        Object coerced;
        if (current instanceof Boolean) {
            coerced = value.getAsBoolean();
        } else if (current instanceof Integer) {
            coerced = value.getAsInt();
        } else if (current instanceof Long) {
            coerced = value.getAsLong();
        } else if (current instanceof Double) {
            coerced = value.getAsDouble();
        } else if (current instanceof Float) {
            coerced = value.getAsFloat();
        } else if (current instanceof String) {
            coerced = value.getAsString();
        } else if (current instanceof Enum<?> enumValue) {
            coerced = parseEnum(enumValue, value.getAsString());
        } else {
            throw new IllegalArgumentException(
                "Setting values of type " + current.getClass().getSimpleName() + " is not supported");
        }

        ((OptionInstance<Object>) option).set(coerced);
    }

    private static Object parseEnum(Enum<?> current, String wanted) {
        for (Object constant : current.getDeclaringClass().getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(wanted)) {
                return constant;
            }
        }
        StringBuilder valid = new StringBuilder();
        for (Object constant : current.getDeclaringClass().getEnumConstants()) {
            if (!valid.isEmpty()) {
                valid.append(", ");
            }
            valid.append(((Enum<?>) constant).name());
        }
        throw new IllegalArgumentException("'" + wanted + "' is not valid here. Valid values: " + valid);
    }

    /** A displayable form of the option's current value. */
    public static String describe(OptionInstance<?> option) {
        Object value = option.get();
        return value == null ? "null" : value.toString();
    }

    public static String typeOf(OptionInstance<?> option) {
        Object value = option.get();
        if (value == null) {
            return "unknown";
        }
        if (value instanceof Enum<?> enumValue) {
            return "enum(" + enumValue.getDeclaringClass().getSimpleName() + ")";
        }
        return value.getClass().getSimpleName().toLowerCase();
    }
}
