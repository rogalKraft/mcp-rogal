package org.wallet.rogalik.mcp.ui;

import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.server.MCPProtocol;
import org.wallet.rogalik.mcp.server.tools.McpTool;
import org.wallet.rogalik.mcp.server.tools.SchemaBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Locale;

public class GetOpenScreenTool implements McpTool {

    @Override
    public String name() {
        return "get_open_screen";
    }

    @Override
    public String description() {
        return "Describe the screen that is currently open: its class, its title, and every widget "
            + "with the text it actually renders plus its rectangle in GUI coordinates.\n\n"
            + "This is the reliable way to work with menus. It answers 'did my click land', 'is "
            + "this button enabled', 'what does this option say right now' without a screenshot, "
            + "and it catches missing or wrong translations immediately - an untranslated widget "
            + "shows its raw key instead of a sentence.\n\n"
            + "Coordinates are in GUI space, the same space the mouse tool and click_widget use.\n\n"
            + "Returns screenOpen=false when the player is in the world with no menu open.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .string("filter", "Only return widgets whose text or type contains this, case-insensitive", false)
            .bool("only_clickable", "Only return widgets that are visible, enabled and have a size", false, false)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        Minecraft client = Minecraft.getInstance();
        Screen screen = ScreenInspector.currentScreen();

        JsonObject result = new JsonObject();
        result.addProperty("guiScale", client.getWindow().getGuiScale());
        result.addProperty("guiWidth", client.getWindow().getGuiScaledWidth());
        result.addProperty("guiHeight", client.getWindow().getGuiScaledHeight());
        result.addProperty("windowWidth", client.getWindow().getWidth());
        result.addProperty("windowHeight", client.getWindow().getHeight());

        if (screen == null) {
            result.addProperty("screenOpen", false);
            result.addProperty("note", "No menu is open - the player is in the world. "
                + "Use open_screen or send_keybind to open one.");
            return MCPProtocol.createSuccessResponse(result.toString());
        }

        result.addProperty("screenOpen", true);
        result.addProperty("screenClass", screen.getClass().getSimpleName());
        result.addProperty("screenClassFull", screen.getClass().getName());
        try {
            result.addProperty("title", screen.getTitle().getString());
        } catch (Exception e) {
            result.addProperty("title", "");
        }

        List<ScreenInspector.WidgetInfo> widgets = ScreenInspector.inspect(screen);

        String filter = arguments.has("filter")
            ? arguments.get("filter").getAsString().toLowerCase(Locale.ROOT)
            : null;
        boolean onlyClickable = arguments.has("only_clickable")
            && arguments.get("only_clickable").getAsBoolean();

        List<ScreenInspector.WidgetInfo> filtered = widgets.stream()
            .filter(w -> !onlyClickable || w.isClickable())
            .filter(w -> filter == null
                || w.text().toLowerCase(Locale.ROOT).contains(filter)
                || w.type().toLowerCase(Locale.ROOT).contains(filter))
            .toList();

        result.add("widgets", ScreenInspector.toJson(filtered));
        result.addProperty("widgetCount", filtered.size());
        result.addProperty("totalWidgets", widgets.size());
        return MCPProtocol.createSuccessResponse(result.toString());
    }
}
