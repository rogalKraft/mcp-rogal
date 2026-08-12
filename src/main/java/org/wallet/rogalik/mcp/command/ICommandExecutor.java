package org.wallet.rogalik.mcp.command;

import com.google.gson.JsonObject;

public interface ICommandExecutor {
    JsonObject executeCommands(JsonObject arguments);
}
