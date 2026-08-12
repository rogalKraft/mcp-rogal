package org.wallet.rogalik.mcp.server.exec;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CommandOutcomeTest {

    @Test
    public void successCarriesTheReturnValue() {
        CommandOutcome outcome = CommandOutcome.succeeded("fill 0 0 0 1 1 1 stone", 8, List.of("Filled 8 blocks"), 12L);
        JsonObject json = outcome.toJson(0);

        assertTrue(json.get("success").getAsBoolean());
        assertEquals(8, json.get("result").getAsInt());
        assertTrue(json.get("error").isJsonNull());
        assertTrue(json.get("errorType").isJsonNull());
        assertEquals("success", json.get("status").getAsString());
        assertTrue(json.get("applied").getAsBoolean());
    }

    @Test
    public void aCommandThatRanButReportedNothingIsNotAnError() {
        // The silent-failure case: a macro missing a $(key), or a function that bailed out.
        // It must be distinguishable from both success and a syntax error.
        CommandOutcome outcome = CommandOutcome.ranWithoutSuccess("function test:noop", 0, List.of(), 3L);
        JsonObject json = outcome.toJson(0);

        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").isJsonNull(), "No error - it executed, it just did nothing");
        assertEquals("failed", json.get("status").getAsString());
        assertTrue(json.get("accepted").getAsBoolean(), "The game did accept it");
        assertFalse(json.get("applied").getAsBoolean());
        assertTrue(outcome.summary().contains("reported no success"));
    }

    @Test
    public void parseErrorReportsCursorAndIsNotAccepted() {
        CommandOutcome outcome = CommandOutcome.parseError("setblock ~ ~ ~ not_a_block", "Unknown block type", 18, 1L);
        JsonObject json = outcome.toJson(0);

        assertFalse(json.get("success").getAsBoolean());
        assertEquals("parse", json.get("errorType").getAsString());
        assertEquals(18, json.get("cursor").getAsInt());
        assertEquals("rejected_by_game", json.get("status").getAsString());
        assertFalse(json.get("accepted").getAsBoolean());
        assertTrue(outcome.summary().contains("position 18"));
    }

    @Test
    public void runtimeErrorIsAcceptedButNotApplied() {
        CommandOutcome outcome = CommandOutcome.runtimeError("kill @e", "NullPointerException: null", List.of(), 4L);
        JsonObject json = outcome.toJson(0);

        assertEquals("runtime", json.get("errorType").getAsString());
        assertEquals("execution_error", json.get("status").getAsString());
        assertTrue(json.get("accepted").getAsBoolean());
        assertFalse(json.get("applied").getAsBoolean());
    }

    @Test
    public void safetyRejectionIsNeverAccepted() {
        CommandOutcome outcome = CommandOutcome.rejectedBySafety("op someone", "command not in allow list");
        JsonObject json = outcome.toJson(0);

        assertEquals("safety", json.get("errorType").getAsString());
        assertEquals("rejected_by_safety", json.get("status").getAsString());
        assertFalse(json.get("accepted").getAsBoolean());
    }

    @Test
    public void remoteIsTheOnlyRemainingUnknownStatus() {
        CommandOutcome outcome = CommandOutcome.sentToRemote("say hi", List.of(), 500L);

        assertEquals("unknown", outcome.legacyStatus(),
            "Only a genuinely unobservable remote execution may report unknown");
        assertEquals("remote", outcome.toJson(0).get("errorType").getAsString());
    }

    @Test
    public void messagesAreExposedUnderBothTheNewAndLegacyKey() {
        CommandOutcome outcome = CommandOutcome.succeeded("time set day", 1, List.of("Set the time to 1000"), 2L);
        JsonObject json = outcome.toJson(3);

        assertEquals(3, json.get("index").getAsInt());
        assertEquals(1, json.getAsJsonArray("messages").size());
        assertEquals("Set the time to 1000", json.getAsJsonArray("messages").get(0).getAsString());
        assertEquals(json.getAsJsonArray("messages"), json.getAsJsonArray("chatMessages"));
    }
}
