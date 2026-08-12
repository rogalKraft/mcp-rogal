package org.wallet.rogalik.mcp.server.exec;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CommandRunnerTest {

    @Test
    public void responseCountsSuccessesAndFailuresSeparately() {
        JsonObject response = CommandRunner.buildResponse(3, List.of(
            CommandOutcome.succeeded("a", 1, List.of(), 1L),
            CommandOutcome.ranWithoutSuccess("b", 0, List.of(), 1L),
            CommandOutcome.parseError("c", "bad", 2, 1L)
        ));

        assertEquals(3, response.get("totalCommands").getAsInt());
        assertEquals(1, response.get("succeededCount").getAsInt());
        assertEquals(2, response.get("failedCount").getAsInt());
        // b was accepted by the game even though it reported nothing; c never parsed.
        assertEquals(2, response.get("acceptedCount").getAsInt());
        assertEquals(1, response.get("appliedCount").getAsInt());
        assertEquals(3, response.getAsJsonArray("results").size());
    }

    @Test
    public void hintExplainsHowToReadFailures() {
        JsonObject failing = CommandRunner.buildResponse(1, List.of(
            CommandOutcome.parseError("setblock oops", "Unknown block", 9, 1L)));
        assertTrue(failing.get("hint").getAsString().contains("errorType"));

        JsonObject clean = CommandRunner.buildResponse(1, List.of(
            CommandOutcome.succeeded("time set day", 1, List.of(), 1L)));
        assertTrue(clean.get("hint").getAsString().contains("get_blocks_in_area"));
    }

    @Test
    public void allFeedbackIsAggregatedAcrossCommands() {
        JsonObject response = CommandRunner.buildResponse(2, List.of(
            CommandOutcome.succeeded("a", 1, List.of("first"), 1L),
            CommandOutcome.succeeded("b", 1, List.of("second", "third"), 1L)
        ));

        JsonArray messages = response.getAsJsonArray("chatMessages");
        assertEquals(3, messages.size());
        assertEquals("first", messages.get(0).getAsString());
        assertEquals("third", messages.get(2).getAsString());
    }

    @Test
    public void resultsAreIndexedInSubmissionOrder() {
        JsonObject response = CommandRunner.buildResponse(2, List.of(
            CommandOutcome.succeeded("first", 1, List.of(), 1L),
            CommandOutcome.succeeded("second", 1, List.of(), 1L)
        ));

        JsonArray results = response.getAsJsonArray("results");
        assertEquals(0, results.get(0).getAsJsonObject().get("index").getAsInt());
        assertEquals("first", results.get(0).getAsJsonObject().get("command").getAsString());
        assertEquals(1, results.get(1).getAsJsonObject().get("index").getAsInt());
    }

    @Test
    public void runAsDefaultsToConsoleWhenAbsent() {
        CommandRunner.RunAs runAs = CommandRunner.RunAs.from(null);

        assertNull(runAs.player());
        assertNull(runAs.permissionLevel(), "No level means keep the source's own permissions");
    }

    @Test
    public void runAsReadsPlayerAndLevel() {
        JsonObject json = new JsonObject();
        json.addProperty("player", "Runner");
        json.addProperty("permission_level", 0);

        CommandRunner.RunAs runAs = CommandRunner.RunAs.from(json);

        assertEquals("Runner", runAs.player());
        assertEquals(0, runAs.permissionLevel());
    }

    @Test
    public void runAsClampsLevelsIntoTheValidRange() {
        JsonObject tooHigh = new JsonObject();
        tooHigh.addProperty("permission_level", 99);
        assertEquals(4, CommandRunner.RunAs.from(tooHigh).permissionLevel());

        JsonObject negative = new JsonObject();
        negative.addProperty("permission_level", -3);
        assertEquals(0, CommandRunner.RunAs.from(negative).permissionLevel());
    }

    @Test
    public void runAsTreatsExplicitNullsAsAbsent() {
        JsonObject json = new JsonObject();
        json.add("player", com.google.gson.JsonNull.INSTANCE);
        json.add("permission_level", com.google.gson.JsonNull.INSTANCE);

        CommandRunner.RunAs runAs = CommandRunner.RunAs.from(json);

        assertNull(runAs.player());
        assertNull(runAs.permissionLevel());
    }
}
