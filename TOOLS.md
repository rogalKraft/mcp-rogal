# Tool reference

26 tools, grouped by what they are for. Everything here was verified against a running
Minecraft 26.2 client; the mod now targets 26.3 (server boots and the HTTP endpoint responds,
but the individual tools below haven't each been re-walked against 26.3 yet).

## Running commands

### `execute_commands`
Runs commands and reports **what actually happened**, not a guess.

| field | meaning |
|---|---|
| `success` | whether the command reported success |
| `result` | the command's return value — the number `/execute store` would capture |
| `error` / `errorType` | `parse` (syntax error, with `cursor`), `runtime`, `safety`, `skipped`, or null |
| `messages` | the feedback the command produced |

Three outcomes that used to be indistinguishable are now separate:

```
setblock ~ ~ ~ minecraft:not_a_block   → errorType=parse, cursor=15
execute if block ~ ~ ~ bedrock run say → success=false, error=null   (ran, matched nothing)
time set day                           → success=true, result=1000
```

The middle case is what a silently failing function or an unarmed trigger looks like.

**`/function` reports `success=false, result=0` unless the function ends with `/return`.** That
is normal Minecraft behaviour, not a failure.

Extra arguments:
- `wait_ticks` — pause after each command, so a later read sees what an earlier write did.
- `run_as: {player, permission_level}` — `execute as X` changes `@s` but **not** permissions.
  This does. At level 0 an operator-only command comes back rejected, which is the only way to
  see what an ordinary player gets.

The response's `executedAs` states whose position and permissions were used, so `~ ~ ~` is never
ambiguous. With exactly one player online, commands run from that player's position.

### `advance_ticks`
Waits N server ticks. Needed because a read issued in the same tick as a write still sees the
old value.

### `validate_command`
Parses without running. Reports the error and cursor position, or with `suggest: true` the
completions available at that point.

## Logs

### `get_logs`
Cursor-based: the response carries `nextId`; pass it back as `since_id` to get only what is new.
`missed` reports entries evicted before your cursor, so a gap is visible rather than silent.
Sources are `game` (the log file) and `chat` (command feedback, which never reaches the file).

### `wait_for_log`
Blocks until a pattern appears, or times out. This is the live half:

```
reload_datapacks()
wait_for_log(pattern: "Failed to load|Couldn't load", min_level: "WARN")
```

Returns the matching entry plus surrounding context.

### `get_crash_reports`
Recent crash reports and the tail of `latest.log`, for when the game died with the buffer.

## Datapacks

### `get_world_info`
World name, save folder, datapack folder — and **the pack formats this build expects**. A wrong
`pack_format` makes Minecraft reject the pack with only a vague "Error reading pack metadata"
while every function in it silently does nothing.

### `files`
`list` / `read` / `write` / `mkdir` / `delete`, confined to an allow-listed set of directories.
Paths resolving outside them — through `..` or a symlink — are refused.

### `reload_datapacks`
Runs `/reload` and returns the log it produced. `/reload` succeeds even when a pack fails to
load, so `logHasWarnings` is the field that matters.

### `list_ids`, `describe_block_state`
Valid ids by registry, and the exact properties a block accepts in `[brackets]` with their
allowed values and defaults.

## Fake players

### `fake_players`
`spawn` / `despawn` / `list`. Real `ServerPlayer`s in the player list — verified:

- `@a` selects them (`execute as @a` ran once per player)
- scoreboards score them
- their UUID is name-derived, so scores survive death and respawn
- `kill` respawns them rather than leaving them dead (they have no client to click Respawn)
- `despawn` fires a real leave event, so disconnect handling runs
- `/tp` moves them between dimensions

Nothing in game logic checks "is this fake and skip" — only the listing tool distinguishes them.

**What they cannot do:** press a dialog button. That needs a client.

## Client control

### `press_key`, `type_text`, `send_keybind`, `list_keybinds`, `mouse`
Input goes through the same entry points the window callbacks use, so it works identically in
the world, in menus and in text fields.

- `press_key` — `tap` / `press` / `release`, with modifiers and `hold_ticks`
- `type_text` — character input; a key event alone types nothing
- `send_keybind` — by binding id, so it survives a rebound key
- `mouse` — GUI coordinates by default (`coord_space: "window"` for screenshot pixels)

### `get_open_screen`
Every widget with its **rendered text** and rectangle. This is how you check a menu without a
screenshot — and how localisation bugs show up immediately, since an untranslated widget renders
its raw key.

List rows (worlds, servers, packs) borrow their geometry from the parent list, so they are
clickable by index.

### `click_widget`
Clicks by visible label or index. An ambiguous label clicks nothing and returns the candidates.
Labels that look destructive (delete, erase, reset world, and their Russian equivalents) are
refused unless `input.allowDestructiveUi` is on **and** `allow_destructive` is passed.

Screens that fade out switch a few ticks later — raise `settle_ticks` or read again.

### `open_screen`, `options`
Open a named screen directly. Read or change settings without dragging a slider.

### `reload_resources`, `rejoin_world`
F3+T has no command. And dynamic registries (`dialog/`, `enchantment/`, `worldgen/`) are only
read when a world loads — `/reload` leaves the old ones in place, which looks exactly like the
change having no effect. `rejoin_world` is the only way to pick them up.

## Config

`config/mcp.json`:

```jsonc
{
  "input":  { "enabled": true, "allowDestructiveUi": false, "maxHoldTicks": 200,
              "preventPauseOnLostFocus": true },
  "logs":   { "bufferSize": 5000, "maxWaitMs": 60000, "captureChat": true,
              "maxBodyLogChars": 2000 },
  "files":  { "enabled": true, "allowedRoots": ["saves","config","datapacks",
              "resourcepacks","logs","crash-reports"], "allowDelete": false },
  "fakePlayers": { "enabled": true, "maxCount": 8 },
  "auth":   { "token": null }
}
```

`preventPauseOnLostFocus` stops singleplayer pausing when you click into another window —
otherwise the pause menu covers the view and every screenshot and screen read reports the pause
menu instead of the game. Escape still pauses.

`auth.token`, when set, requires `Authorization: Bearer <token>`. The server refuses to bind to
a non-loopback host without one: this endpoint runs commands, injects input and writes files.

## Not implemented

- **Fake player movement.** Teleporting is deterministic and covers position, dimension, death
  and disconnect; walking is only needed for distance-threshold rules. Baritone does not fit —
  it drives the *local* player through a client input controller, and a fake player has no client.
- **`profile_ticks`.** `/perf start|stop` covers it in the meantime.
- **Rich `get_player_info`.** Still returns the basics; full inventory with item components as
  SNBT, effects, attributes and scoreboard values are not there yet.
