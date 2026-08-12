# Architecture and contributor notes

Orientation for anyone working on this codebase, human or agent.

## What this is

**MCP-rogal** — a Fabric mod for Minecraft 26.2 that runs an HTTP MCP server and exposes 26 tools
aimed at developing mods and datapacks. Started from
[cuspymd/mcp-server-mod](https://github.com/cuspymd/mcp-server-mod) (CC0-1.0).

Mod id `mcp-rogal`, package `org.wallet.rogalik.mcp`, endpoint `http://localhost:8080/mcp`.

## Commands

```bash
./gradlew build          # compile, run tests, produce the jar
./gradlew test           # unit tests only (134 of them)
./gradlew runClient      # dev client with the mod loaded
./gradlew runServer      # dev dedicated server
```

The jar lands in `build/libs/mcp-rogal-<version>.jar`.

## Verifying against a running game

Unit tests cover the pure logic; anything touching Minecraft has to be checked live. Start
`runClient`, wait for `HTTP MCP Server started` in the log, then drive it over HTTP:

```powershell
$body = '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_open_screen","arguments":{}}}'
Invoke-RestMethod -Uri "http://localhost:8080/mcp" -Method Post -Body $body `
  -ContentType "application/json" -Headers @{Accept="application/json"}
```

A world must be loaded for most tools. The mod's own tools can drive the menus to load one:
`open_screen(world_select)` → `get_open_screen` → `click_widget`.

## Checking Minecraft APIs

26.2 renamed and restructured a lot. **Never guess a signature** — check it:

```powershell
$jar="$env:USERPROFILE\.gradle\caches\fabric-loom\26.2\neoforge\26.2.0.51-beta\minecraft-merged-official.jar"
javap -p -cp $jar net.minecraft.client.MouseHandler
```

Things that already caught this project out:

- Permissions are `PermissionSet`, not an int. Use
  `LevelBasedPermissionSet.forLevel(PermissionLevel.byId(n))`.
- The current screen lives on `Minecraft.gui` (`gui.screen()` / `gui.setScreen()`), not on
  `Minecraft`.
- `Window.handle()`, not `getWindow()`.
- Input events are records: `KeyEvent(key, scancode, mods)`, `CharacterEvent(codepoint)`,
  `MouseButtonInfo(button, mods)`. The handler methods taking them are private.
- `ServerPlayer` pulls `WaypointTransmitter.Connection` into scope and shadows
  `net.minecraft.network.Connection` — qualify it.
- `GameProfile` is a record: `name()`, not `getName()`.

## Architecture

### Source sets
Fabric's split environment. `src/main` is shared and runs on a dedicated server; `src/client` is
client-only and may reference `Minecraft`. `src/main` must never import client classes.

```
src/main/java/org/wallet/rogalik/mcp/
├── MCPServerMod.java              # common entry: Platform, fake-player respawn tick
├── MCPServerModServer.java        # dedicated server entry, builds its ToolRegistry
├── bridge/HTTPMCPServer.java      # HTTP + JSON-RPC, auth, log truncation
├── server/tools/                  # McpTool, ToolRegistry, SchemaBuilder, most tools
├── server/exec/                   # CommandRunner, CommandOutcome, ServerTicks
├── server/fakeplayer/             # FakePlayer, manager, registry, respawner
├── logs/                          # LogRingBuffer, McpLogAppender, McpLogs
├── files/GameFileAccess.java      # path sandbox
├── config/MCPConfig.java
└── platform/                      # Platform interface + FabricPlatform

src/client/java/org/wallet/rogalik/mcp/
├── MCPServerModClient.java        # client entry, builds the full ToolRegistry
├── input/                         # InputDispatcher, KeyCodes, key/mouse tools
├── ui/                            # ScreenInspector, screen and options tools
├── command/                       # ClientCommandExecutor, ChatMessageCapture
├── utils/                         # ClientTickScheduler, PauseGuard, screenshots, scanning
└── mixin/client/                  # accessors for KeyboardHandler, MouseHandler, MinecraftServer
```

### Adding a tool
Implement `McpTool` (name, description, inputSchema, call) and register it in the relevant entry
point. Build schemas with `SchemaBuilder`, not by hand. Return responses through
`MCPProtocol.createSuccessResponse` / `createErrorResponse`.

A tool that only makes sense on one side is simply not registered on the other — there is no
availability flag.

### Command execution
`CommandRunner` runs on the server thread against the real dispatcher. It parses first
(`Commands.validateParseResults`) so a syntax error is reported separately with its cursor, then
executes with a `CommandSourceStack.withCallback` that captures success and the return value.

In singleplayer `ClientCommandExecutor` delegates to it through the integrated server. Only when
connected to someone else's server does it fall back to sending a packet, where the outcome is
genuinely unobservable — that is the sole remaining `status: "unknown"`.

With exactly one player online, commands default to that player's position so `~ ~ ~` means what
a caller expects. The response's `executedAs` says which source was used.

### Client thread
Everything touching the client goes through `InputDispatcher.onClientThread` or
`ClientTickScheduler`. Never block the client thread waiting on a future; the HTTP handlers run on
their own executor, so block there instead.

## Conventions

- Comments explain *why*, never *what*. If a line needs a comment to say what it does, rewrite it.
- Tool descriptions are prompt text — they are what an assistant reads to decide how to use the
  tool. Say what the tool is for and what the traps are, concretely.
- Every new piece of pure logic gets unit tests. Sandbox escapes, cursor arithmetic and name
  parsing are exactly where bugs hide.
- Do not add "is this a fake player, skip it" checks anywhere in game logic. Fake players are
  meant to be indistinguishable; the listing tool is the only place that may tell them apart.

## Config

`config/mcp.json`, sections: `server`, `client`, `safety`, `input`, `logs`, `files`,
`fakePlayers`, `auth`. Missing sections fall back to defaults; a section written as an explicit
`null` is normalised by `fillMissingSections()`.

## Known gaps

See `TOOLS.md` under "Not implemented": rich `get_player_info` (inventory with item components as
SNBT, effects, attributes, scoreboard), `profile_ticks`, fake-player pathfinding, and
`click_dialog_button` verification against a real dialog.
