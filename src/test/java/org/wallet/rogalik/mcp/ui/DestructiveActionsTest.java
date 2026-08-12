package org.wallet.rogalik.mcp.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DestructiveActionsTest {

    @Test
    public void deletionLabelsAreFlagged() {
        assertTrue(DestructiveActions.isDestructive("Delete"));
        assertTrue(DestructiveActions.isDestructive("Delete World"));
        assertTrue(DestructiveActions.isDestructive("DELETE"));
        assertTrue(DestructiveActions.isDestructive("Erase all data"));
    }

    @Test
    public void russianLabelsAreFlagged() {
        assertTrue(DestructiveActions.isDestructive("Удалить мир"));
        assertTrue(DestructiveActions.isDestructive("Сбросить настройки"));
    }

    @Test
    public void resetOfWorldOrDefaultsIsFlagged() {
        assertTrue(DestructiveActions.isDestructive("Reset World"));
        assertTrue(DestructiveActions.isDestructive("Reset to Default"));
        assertTrue(DestructiveActions.isDestructive("Re-create World"));
    }

    @Test
    public void removingAWorldOrPackIsFlagged() {
        assertTrue(DestructiveActions.isDestructive("Remove Server"));
        assertTrue(DestructiveActions.isDestructive("Remove pack"));
    }

    @Test
    public void ordinaryNavigationIsNotFlagged() {
        assertFalse(DestructiveActions.isDestructive("Done"));
        assertFalse(DestructiveActions.isDestructive("Back"));
        assertFalse(DestructiveActions.isDestructive("Video Settings..."));
        assertFalse(DestructiveActions.isDestructive("Play Selected World"));
        assertFalse(DestructiveActions.isDestructive("Create New World"));
    }

    @Test
    public void wordBoundariesPreventFalsePositives() {
        // "Undeleted" and "Preset" contain the letters but are not deletion actions.
        assertFalse(DestructiveActions.isDestructive("Undeleted items"));
        assertFalse(DestructiveActions.isDestructive("Graphics Preset"));
    }

    @Test
    public void emptyLabelsAreSafe() {
        assertFalse(DestructiveActions.isDestructive(""));
        assertFalse(DestructiveActions.isDestructive("   "));
        assertFalse(DestructiveActions.isDestructive(null));
    }

    @Test
    public void reasonNamesTheOffendingLabel() {
        String reason = DestructiveActions.reasonFor("Delete World").orElseThrow();

        assertTrue(reason.contains("delete world"), reason);
    }
}
