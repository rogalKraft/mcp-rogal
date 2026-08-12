package org.wallet.rogalik.mcp.utils;

import com.google.gson.JsonObject;

public interface IPlayerInfoProvider {

    JsonObject getPlayerInfo();

    /**
     * Reads a specific player, selected by the {@code player} argument.
     *
     * <p>Implementations that cannot yet target a named player fall back to the default subject.
     */
    default JsonObject getPlayerInfo(JsonObject arguments) {
        return getPlayerInfo();
    }
}
