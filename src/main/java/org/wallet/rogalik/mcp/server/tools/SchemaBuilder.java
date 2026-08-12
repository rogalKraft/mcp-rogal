package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Fluent builder for JSON Schema objects describing tool inputs.
 *
 * <p>Hand-rolling these with bare {@code new JsonObject()} runs to about thirty lines per tool,
 * which is why the original four tool schemas dwarf the logic they describe.
 */
public final class SchemaBuilder {

    private final JsonObject properties = new JsonObject();
    private final JsonArray required = new JsonArray();

    private SchemaBuilder() {
    }

    public static SchemaBuilder object() {
        return new SchemaBuilder();
    }

    /** Schema for a tool that takes no arguments at all. */
    public static JsonObject empty() {
        return object().build();
    }

    public SchemaBuilder string(String name, String description, boolean isRequired) {
        return add(name, primitive("string", description), isRequired);
    }

    public SchemaBuilder integer(String name, String description, boolean isRequired) {
        return add(name, primitive("integer", description), isRequired);
    }

    public SchemaBuilder number(String name, String description, boolean isRequired) {
        return add(name, primitive("number", description), isRequired);
    }

    public SchemaBuilder bool(String name, String description, boolean isRequired) {
        return add(name, primitive("boolean", description), isRequired);
    }

    public SchemaBuilder bool(String name, String description, boolean isRequired, boolean defaultValue) {
        JsonObject property = primitive("boolean", description);
        property.addProperty("default", defaultValue);
        return add(name, property, isRequired);
    }

    public SchemaBuilder integer(String name, String description, boolean isRequired, int defaultValue) {
        JsonObject property = primitive("integer", description);
        property.addProperty("default", defaultValue);
        return add(name, property, isRequired);
    }

    /** A string constrained to a fixed set of values. */
    public SchemaBuilder enumString(String name, String description, boolean isRequired, String... values) {
        JsonObject property = primitive("string", description);
        JsonArray allowed = new JsonArray();
        for (String value : values) {
            allowed.add(value);
        }
        property.add("enum", allowed);
        return add(name, property, isRequired);
    }

    /** An array whose items are a bare primitive of the given type. */
    public SchemaBuilder arrayOf(String name, String itemType, String description, boolean isRequired) {
        JsonObject property = primitive("array", description);
        JsonObject items = new JsonObject();
        items.addProperty("type", itemType);
        property.add("items", items);
        return add(name, property, isRequired);
    }

    /** Nests an already-built schema, e.g. one produced by {@link #blockPosition(String)}. */
    public SchemaBuilder nested(String name, JsonObject schema, String description, boolean isRequired) {
        JsonObject property = schema.deepCopy();
        property.addProperty("description", description);
        return add(name, property, isRequired);
    }

    /** Shared {x, y, z} integer-coordinate schema. */
    public static JsonObject blockPosition(String description) {
        return object()
            .integer("x", "X coordinate", true)
            .integer("y", "Y coordinate (vertical; 64 is typical ground level)", true)
            .integer("z", "Z coordinate", true)
            .build(description);
    }

    private SchemaBuilder add(String name, JsonObject property, boolean isRequired) {
        properties.add(name, property);
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    private static JsonObject primitive(String type, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", type);
        if (description != null) {
            property.addProperty("description", description);
        }
        return property;
    }

    public JsonObject build() {
        return build(null);
    }

    public JsonObject build(String description) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        if (description != null) {
            schema.addProperty("description", description);
        }
        schema.add("properties", properties);
        if (!required.isEmpty()) {
            schema.add("required", required);
        }
        return schema;
    }
}
