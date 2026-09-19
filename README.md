# MCP-rogal

An MCP server for building Minecraft mods and datapacks. Point an AI assistant at your running
game and it can write a datapack, reload it, run it, read the error the game actually produced,
and fix it.

## Overview

The mod runs an HTTP server inside the Minecraft client or a dedicated server and exposes 26 MCP
tools to it. The point of difference from a plain command bridge is that every command comes back
with what really happened — a real success flag, the command's return value, and parse errors with
the cursor position — so a command that ran and quietly did nothing is distinguishable from one
that never parsed.

Works in single-player (through the integrated server) and on dedicated servers. On a dedicated
server the tools that need a screen are simply not registered.

Full tool reference: **[TOOLS.md](TOOLS.md)** · Page copy: [DESCRIPTION.md](DESCRIPTION.md)

## Features

- **Server and Client Support**: Works on both single-player and dedicated server environments.
- **MCP Protocol Support**: Full implementation of Model Context Protocol for AI interaction
- **Honest command results**: real success flag, return value, and syntax errors with a cursor
  position — a command that runs but does nothing is distinguishable from one that never parsed
- **Live logs**: cursor-based reads and a blocking wait-for-pattern, including in-game chat
- **Client control**: keyboard, mouse, menus and settings, with rendered widget text readable directly
- **Datapack loop**: sandboxed file access, `/reload`, and the pack formats this build expects
- **Fake players**: real server-side players for testing multiplayer behaviour
- **Safety Validation**: Comprehensive command filtering and validation system
- **Configurable Settings**: Customizable safety limits, server settings, and command permissions

## Requirements

- **Minecraft**: 26.3
- **Fabric Loader**: 0.19.5 or higher
- **Fabric API**: 0.161.0+26.3 or higher compatible 26.3 build
- **Java**: 25 or higher

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.3
2. Download and install a Minecraft 26.3-compatible [Fabric API](https://modrinth.com/mod/fabric-api)
3. Place the mod JAR file in your `mods` folder
4. Launch Minecraft with the Fabric profile

Minecraft 26.3 is unobfuscated. This mod is built against Mojang official names and does not use Yarn mappings.

## Usage

### Starting the MCP Server

The MCP server starts automatically when you launch Minecraft with the mod installed. By default, it runs on `localhost:8080`.

### Important Command Permission Settings

Before using AI command tools, make sure command input is allowed in your current game mode:
- **Single Player**: When creating a world, set **Allow Cheats** to **ON**.
- **Multiplayer / Dedicated Server**: The player running commands must have server permission (for example, OP or equivalent permission from your server permission plugin).

Switching to another window while the mod drives the game is handled for you: the mod turns off
**Pause on Lost Focus** at startup, so the pause menu never ends up covering the view and being
reported by screenshots and screen reads. Escape still pauses. Set
`input.preventPauseOnLostFocus` to `false` in `config/mcp.json` to keep vanilla behaviour.

### Server vs Client Modes

The mod detects if it is running in a Client (Single Player) or a Dedicated Server environment:
- **Client Mode**: Full feature support, including the `take_screenshot` tool, which uses the local game window.
- **Dedicated Server Mode**: Has access to tools like `execute_commands`, `get_player_info`, and `get_blocks_in_area`, enabling full AI manipulation of the world without rendering. The `take_screenshot` tool is disabled in server mode since there is no rendering context. Note that `get_player_info` currently selects the first online player on the server to report its location.

If playing Single Player, the integrated server logic runs through the client-side MCP.

### Configuration

The mod creates a configuration file at `config/mcp.json`:

```jsonc
{
  "server": { "port": 8080, "host": "localhost", "autoStart": true,
              "enableSafety": true, "maxAreaSize": 10, "requestTimeoutMs": 30000,
              "allowedCommands": ["fill", "clone", "setblock", "summon", "tp", "give",
                                  "gamemode", "effect", "enchant", "weather", "time",
                                  "say", "tell", "title"] },
  "client": { "showNotifications": true, "logLevel": "INFO", "logCommands": false,
              "saveScreenshotsForDebug": false },
  "safety": { "maxEntitiesPerCommand": 10, "maxBlocksPerCommand": 125000,
              "blockCreativeForAll": true, "requireOpForAdminCommands": true },
  "input":  { "enabled": true, "allowDestructiveUi": false, "maxHoldTicks": 200,
              "preventPauseOnLostFocus": true },
  "logs":   { "enabled": true, "bufferSize": 5000, "maxWaitMs": 60000,
              "captureChat": true, "minLevel": "INFO", "maxBodyLogChars": 2000 },
  "files":  { "enabled": true, "allowDelete": false, "maxFileSizeBytes": 2097152,
              "allowedRoots": ["saves", "config", "datapacks", "resourcepacks",
                               "logs", "crash-reports"] },
  "fakePlayers": { "enabled": true, "maxCount": 8 },
  "auth":   { "token": null }
}
```

A section left out of the file falls back to its defaults, so you only need to write what you
change.

- `server.requestTimeoutMs` caps how long the server waits for a tool before reporting a timeout.
- `logs.maxWaitMs` is separate, because `wait_for_log` is meant to block for longer than a
  normal request.
- `auth.token`, when set, requires an `Authorization: Bearer <token>` header. The server refuses
  to bind to a non-loopback host without one — this endpoint runs commands, injects input and
  writes files.

### Connecting with AI Assistants

Point your AI assistant at the endpoint:
```
http://localhost:8080/mcp
```

It is plain streamable HTTP, so any MCP-capable client takes it. In your client's MCP config:

```json
{
  "mcpServers": {
    "minecraft": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

26 tools are exposed, in five groups:

| Group | Tools |
|---|---|
| Commands and timing | `execute_commands`, `advance_ticks`, `validate_command` |
| Logs | `get_logs`, `wait_for_log`, `get_crash_reports` |
| Datapacks | `files`, `reload_datapacks`, `get_world_info`, `list_ids`, `describe_block_state` |
| Multiplayer | `fake_players` |
| Client control | `press_key`, `type_text`, `send_keybind`, `list_keybinds`, `mouse`, `get_open_screen`, `click_widget`, `open_screen`, `options`, `reload_resources`, `rejoin_world`, `take_screenshot` |

Plus `get_player_info` and `get_blocks_in_area`.

**[TOOLS.md](TOOLS.md) documents every one of them**, with the arguments and the traps.

### Example Commands

The AI can execute commands like:
- `fill ~ ~ ~ ~10 ~5 ~8 oak_planks` - Fill an area with blocks
- `summon villager ~ ~ ~` - Spawn entities
- `setblock ~ ~1 ~ oak_door` - Place specific blocks
- `tp @s ~ ~10 ~` - Teleport players
- `give @s diamond_sword` - Give items

## Safety Features

### Allowed Commands
- Building: `fill`, `clone`, `setblock`
- Entities: `summon`, `tp`, `teleport`
- Items: `give`
- Game state: `gamemode`, `effect`, `enchant`, `weather`, `time`
- Communication: `say`, `tell`, `title`

### Blocked Operations
- Mass entity destruction (`kill @a`, `kill @e`)
- Excessive area operations (>50×50×50 blocks)
- Mass item generation (>100 items)
- Global creative mode assignment

## Development

### Building

```bash
./gradlew build
```

### Running in Development

```bash
./gradlew runClient
```

### Project Structure

```
src/
├── main/java/org/wallet/rogalik/mcp/
│   ├── MCPServerMod.java           # Main mod class
│   ├── MCPServerModClient.java     # Client initializer
│   ├── server/                     # MCP server implementation
│   ├── command/                    # Command execution system
│   ├── config/                     # Configuration management
│   └── utils/                      # Utility classes
└── main/resources/
    ├── fabric.mod.json             # Mod metadata
    └── *.mixins.json              # Mixin configurations
```

## API Reference

The endpoint speaks JSON-RPC over a single HTTP path, `POST /mcp`, with the standard MCP methods:
`initialize`, `ping`, `tools/list`, `tools/call`.

Every tool's arguments and response are described in **[TOOLS.md](TOOLS.md)**; the schemas are
also served by `tools/list`, so an assistant discovers them on its own.

One thing worth stating here, because it is the behaviour most likely to surprise anyone coming
from a plain command bridge — `execute_commands` reports per command:

| field | meaning |
|---|---|
| `success` | whether the command reported success |
| `result` | the command's return value, the number `/execute store` would capture |
| `error`, `errorType` | `parse` (with `cursor`), `runtime`, `safety`, `skipped`, or null |
| `messages` | the feedback the command produced |

So these are three distinguishable outcomes:

```
time set day                            → success=true    result=1000
setblock ~ ~ ~ minecraft:not_a_block    → errorType=parse  cursor=15
execute if block ~ ~ ~ bedrock run say  → success=false    error=null
```

The last is a command that parsed, ran, and did nothing — a condition that did not match, a
function that returned early, a trigger that was never armed.

`/function` reports `success=false` and `result=0` unless the function ends with `/return`. That
is vanilla behaviour, not a failure.

The legacy `status` / `accepted` / `applied` fields are still present and derived from the same
data, so older clients keep working.

**Example request:**
```json
{
  "method": "tools/call",
  "params": {
    "name": "execute_commands",
    "arguments": {
      "commands": ["fill ~ ~ ~ ~10 ~5 ~8 oak_planks", "setblock ~5 ~6 ~4 oak_door"],
      "wait_ticks": 1
    }
  }
}
```

## Debugging

### Local Screenshot Storage

For debugging purposes, you can enable local saving of every screenshot captured by the MCP server.

1. Open `config/mcp.json`.
2. Set `"save_screenshots_for_debug": true` in the `client` section.
3. Screenshots will be saved to the `mcp_debug_screenshots/` directory in your Minecraft instance folder.
4. Files are named using the pattern: `screenshot_YYYYMMDD_HHMMSS_SSS.png`.

## License

This project is licensed under the MIT License.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly (See [TESTING.md](TESTING.md) for more info)
5. Submit a pull request

## Support

For issues and questions:
- Check the [Issues](https://github.com/your-repo/issues) page
- Review the configuration documentation
- Enable debug logging for detailed troubleshooting
