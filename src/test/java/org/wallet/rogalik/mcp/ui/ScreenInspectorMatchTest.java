package org.wallet.rogalik.mcp.ui;

import org.wallet.rogalik.mcp.ui.ScreenInspector.Match;
import org.wallet.rogalik.mcp.ui.ScreenInspector.WidgetInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ScreenInspectorMatchTest {

    private static WidgetInfo widget(int index, String text) {
        return new WidgetInfo(index, 0, "Button", text, null, 10, 20, 100, 20,
            true, true, false, false);
    }

    private static WidgetInfo disabled(int index, String text) {
        return new WidgetInfo(index, 0, "Button", text, null, 10, 20, 100, 20,
            false, true, false, false);
    }

    private static WidgetInfo withoutBounds(int index, String text) {
        return new WidgetInfo(index, 0, "Label", text, null, null, null, null, null,
            true, true, false, false);
    }

    @Test
    public void exactMatchWins() {
        List<WidgetInfo> widgets = List.of(widget(0, "Done"), widget(1, "Done Editing"));

        Match match = ScreenInspector.findByLabel(widgets, "Done");

        assertTrue(match.isFound());
        assertEquals(0, match.widget().index());
    }

    @Test
    public void matchingIsCaseInsensitiveAndIgnoresSurroundingSpace() {
        List<WidgetInfo> widgets = List.of(widget(0, "Render Distance"));

        assertTrue(ScreenInspector.findByLabel(widgets, "  render distance ").isFound());
    }

    @Test
    public void uniqueSubstringMatches() {
        List<WidgetInfo> widgets = List.of(widget(0, "Video Settings..."), widget(1, "Music & Sounds..."));

        Match match = ScreenInspector.findByLabel(widgets, "video");

        assertTrue(match.isFound());
        assertEquals(0, match.widget().index());
    }

    @Test
    public void ambiguousSubstringIsRefusedRatherThanGuessed() {
        // Clicking the wrong button in a menu is not something a caller can undo.
        List<WidgetInfo> widgets = List.of(widget(0, "Delete World"), widget(1, "Delete Pack"));

        Match match = ScreenInspector.findByLabel(widgets, "delete");

        assertFalse(match.isFound());
        assertTrue(match.isAmbiguous());
        assertEquals(2, match.candidates().size());
    }

    @Test
    public void anExactMatchWinsEvenWhenDisabled() {
        // Returning the greyed-out button the caller named - so click_widget can report that it
        // is disabled - beats silently clicking a different button whose label merely contains it.
        List<WidgetInfo> widgets = List.of(disabled(0, "Join Server"), widget(1, "Join Server Later"));

        Match match = ScreenInspector.findByLabel(widgets, "join server");

        assertTrue(match.isFound());
        assertEquals(0, match.widget().index());
        assertFalse(match.widget().isClickable());
    }

    @Test
    public void ambiguousSubstringsAreResolvedByClickability() {
        // No exact match here, so both are substring candidates and clickability decides.
        List<WidgetInfo> widgets = List.of(disabled(0, "Open Folder"), widget(1, "Open Folder Now"));

        Match match = ScreenInspector.findByLabel(widgets, "folder");

        assertTrue(match.isFound());
        assertEquals(1, match.widget().index());
    }

    @Test
    public void duplicateExactLabelsAreResolvedByClickability() {
        List<WidgetInfo> widgets = List.of(disabled(0, "Done"), widget(1, "Done"));

        Match match = ScreenInspector.findByLabel(widgets, "Done");

        assertTrue(match.isFound());
        assertEquals(1, match.widget().index());
    }

    @Test
    public void noMatchIsDistinctFromAmbiguous() {
        Match match = ScreenInspector.findByLabel(List.of(widget(0, "Back")), "nonexistent");

        assertFalse(match.isFound());
        assertFalse(match.isAmbiguous());
        assertTrue(match.candidates().isEmpty());
    }

    @Test
    public void widgetsWithoutBoundsAreNotClickable() {
        assertFalse(withoutBounds(0, "Some label").isClickable());
        assertFalse(disabled(0, "Greyed out").isClickable());
        assertTrue(widget(0, "Enabled").isClickable());
    }

    @Test
    public void zeroSizedWidgetsAreNotClickable() {
        WidgetInfo collapsed = new WidgetInfo(0, 0, "Button", "Hidden", null, 5, 5, 0, 0,
            true, true, false, false);

        assertFalse(collapsed.isClickable());
    }

    @Test
    public void jsonIncludesTheClickTargetCentre() {
        var json = widget(3, "Done").toJson();

        assertEquals(3, json.get("index").getAsInt());
        assertEquals(60, json.get("centerX").getAsInt());
        assertEquals(30, json.get("centerY").getAsInt());
        assertTrue(json.get("clickable").getAsBoolean());
    }
}
