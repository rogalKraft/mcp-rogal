package org.wallet.rogalik.mcp.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntUnaryOperator;

/**
 * Reads the widget tree of the open screen.
 *
 * <p>Reporting the rendered text of every widget turns menu work from guesswork over a screenshot
 * into something addressable by name — and makes localisation bugs visible without rendering
 * anything at all.
 */
public final class ScreenInspector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScreenInspector.class);

    /** Depth cap; screen trees are shallow, and a cycle would otherwise hang the client thread. */
    private static final int MAX_DEPTH = 12;

    /** Fallback row height when a list will not reveal its spacing. */
    private static final int DEFAULT_ROW_HEIGHT = 36;

    private ScreenInspector() {
    }

    /** One widget, flattened out of the tree with a stable index. */
    public record WidgetInfo(
        int index,
        int depth,
        String type,
        String text,
        String value,
        Integer x,
        Integer y,
        Integer width,
        Integer height,
        boolean active,
        boolean visible,
        boolean focused,
        boolean hovered
    ) {

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("index", index);
            json.addProperty("depth", depth);
            json.addProperty("type", type);
            json.addProperty("text", text);
            if (value != null) {
                json.addProperty("value", value);
            }
            if (x != null) {
                json.addProperty("x", x);
                json.addProperty("y", y);
                json.addProperty("width", width);
                json.addProperty("height", height);
                json.addProperty("centerX", x + width / 2);
                json.addProperty("centerY", y + height / 2);
            }
            json.addProperty("active", active);
            json.addProperty("visible", visible);
            json.addProperty("focused", focused);
            json.addProperty("hovered", hovered);
            json.addProperty("clickable", isClickable());
            return json;
        }

        /** A widget can only be clicked meaningfully if it is on screen, enabled and has geometry. */
        public boolean isClickable() {
            return active && visible && x != null && width != null && width > 0 && height > 0;
        }
    }

    public static Screen currentScreen() {
        Minecraft client = Minecraft.getInstance();
        return client.gui == null ? null : client.gui.screen();
    }

    /** Flattens the widget tree of the open screen, depth first, in render order. */
    public static List<WidgetInfo> inspect(Screen screen) {
        List<WidgetInfo> widgets = new ArrayList<>();
        if (screen == null) {
            return widgets;
        }
        collect(screen.children(), widgets, 0, null);
        return widgets;
    }

    /**
     * Geometry of a scrolling list's rows.
     *
     * <p>List entries are not widgets and carry no position of their own, so without borrowing
     * the parent list's row layout every world, server and pack row would be unclickable.
     */
    private record RowGeometry(int left, int width, IntUnaryOperator top, int height) {
    }

    private static void collect(
        List<? extends GuiEventListener> children,
        List<WidgetInfo> out,
        int depth,
        RowGeometry rows
    ) {
        if (children == null || depth > MAX_DEPTH) {
            return;
        }

        int rowIndex = 0;
        for (GuiEventListener child : children) {
            if (child == null) {
                continue;
            }
            out.add(describe(child, out.size(), depth, rows, rowIndex++));

            // Lists and layout containers hold the widgets that actually matter, so recurse.
            if (child instanceof ContainerEventHandler container) {
                collect(container.children(), out, depth + 1, rowGeometryOf(child));
            }
        }
    }

    private static RowGeometry rowGeometryOf(GuiEventListener element) {
        if (!(element instanceof AbstractSelectionList<?> list)) {
            return null;
        }
        try {
            // Row height is not exposed, so derive it from the gap between two rows. Done
            // separately because a list may refuse an index it has no entry for.
            int height = DEFAULT_ROW_HEIGHT;
            try {
                int gap = list.getRowTop(1) - list.getRowTop(0);
                if (gap > 0) {
                    height = gap;
                }
            } catch (Exception ignored) {
                // Keep the default; a wrong height still lands a click inside the row.
            }
            return new RowGeometry(list.getRowLeft(), list.getRowWidth(), list::getRowTop, height);
        } catch (Exception e) {
            LOGGER.warn("Could not read row geometry from {}", list.getClass().getSimpleName(), e);
            return null;
        }
    }

    private static WidgetInfo describe(
        GuiEventListener element,
        int index,
        int depth,
        RowGeometry rows,
        int rowIndex
    ) {
        String type = element.getClass().getSimpleName();
        if (type.isEmpty()) {
            // Anonymous subclasses are common for one-off buttons; name them after their parent.
            type = element.getClass().getSuperclass() != null
                ? element.getClass().getSuperclass().getSimpleName()
                : "GuiEventListener";
        }

        String text = "";
        String value = null;
        Integer x = null;
        Integer y = null;
        Integer width = null;
        Integer height = null;
        boolean active = true;
        boolean visible = true;
        boolean hovered = false;

        if (element instanceof AbstractWidget widget) {
            text = safeText(widget);
            x = widget.getX();
            y = widget.getY();
            width = widget.getWidth();
            height = widget.getHeight();
            active = widget.isActive();
            visible = widget.visible;
            hovered = widget.isHovered();
        }

        if (element instanceof EditBox editBox) {
            value = editBox.getValue();
        }

        // A list row has no geometry of its own; take it from the list that renders it.
        if (x == null && rows != null) {
            x = rows.left();
            y = rows.top().applyAsInt(rowIndex);
            width = rows.width();
            height = rows.height();
        }

        return new WidgetInfo(index, depth, type, text, value, x, y, width, height,
            active, visible, element.isFocused(), hovered);
    }

    private static String safeText(AbstractWidget widget) {
        try {
            return widget.getMessage() == null ? "" : widget.getMessage().getString();
        } catch (Exception e) {
            // A widget whose label resolves lazily can throw before the screen is fully built.
            return "";
        }
    }

    /**
     * Finds a widget by its visible label.
     *
     * <p>Prefers an exact, case-insensitive match, then a unique substring match. A substring that
     * matches several widgets is rejected rather than guessed at, because picking the wrong button
     * in a menu is not a recoverable mistake.
     */
    public static Match findByLabel(List<WidgetInfo> widgets, String label) {
        String wanted = label.trim().toLowerCase(Locale.ROOT);

        List<WidgetInfo> exact = widgets.stream()
            .filter(w -> w.text() != null && w.text().trim().toLowerCase(Locale.ROOT).equals(wanted))
            .toList();
        if (exact.size() == 1) {
            return Match.found(exact.get(0));
        }
        if (exact.size() > 1) {
            List<WidgetInfo> clickable = exact.stream().filter(WidgetInfo::isClickable).toList();
            return clickable.size() == 1 ? Match.found(clickable.get(0)) : Match.ambiguous(exact);
        }

        List<WidgetInfo> partial = widgets.stream()
            .filter(w -> w.text() != null && w.text().toLowerCase(Locale.ROOT).contains(wanted))
            .toList();
        if (partial.isEmpty()) {
            return Match.notFound();
        }
        if (partial.size() == 1) {
            return Match.found(partial.get(0));
        }

        List<WidgetInfo> clickable = partial.stream().filter(WidgetInfo::isClickable).toList();
        return clickable.size() == 1 ? Match.found(clickable.get(0)) : Match.ambiguous(partial);
    }

    /** Outcome of a label lookup. */
    public record Match(WidgetInfo widget, List<WidgetInfo> candidates) {

        public static Match found(WidgetInfo widget) {
            return new Match(widget, List.of(widget));
        }

        public static Match ambiguous(List<WidgetInfo> candidates) {
            return new Match(null, candidates);
        }

        public static Match notFound() {
            return new Match(null, List.of());
        }

        public boolean isFound() {
            return widget != null;
        }

        public boolean isAmbiguous() {
            return widget == null && !candidates.isEmpty();
        }
    }

    public static JsonArray toJson(List<WidgetInfo> widgets) {
        JsonArray array = new JsonArray();
        widgets.forEach(widget -> array.add(widget.toJson()));
        return array;
    }
}
