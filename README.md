# ᴍɪɴᴛ ᴘᴠᴘ

A light mint-green, small-caps themed PvP plugin for **Paper 26.3** (Java 25).

## Features
- Mint hex palette + small-caps chat, sidebar, titles (configurable in `config.yml`)
- Admin-made kits, editable per-player layouts, random duel queue, custom mint GUIs
- Instant respawn, keep-inventory
- Combat tag with action-bar timer, command blocking, combat-log punishment
- Persistent kills / deaths / K/D / kill streaks, streak announcements
- Arenas + 1v1 duels (see below)
- `/stats [player]`, `/leaderboard [kills|kd|streak]`, `/kit`, `/mintpvp reload`

## Build
```bash
mvn package   # needs JDK 25; jar ends up in target/
```
Drop `target/MintPvP-1.0.0.jar` into your server's `plugins/` folder.

## Permissions
- `mintpvp.admin` – reload (default: op)
- `mintpvp.bypass.combat` – ignore combat command blocking (default: op)

## Arenas & duels
Admins set up arenas, then players challenge each other:
```
/arena create <name>        # then stand at each spawn point:
/arena setspawn1 <name>
/arena setspawn2 <name>
/arena setcenter <name>     # optional; defaults to the midpoint of the two spawns
/arena setradius <name> <blocks>   # border half-width, default 40 (80x80 area)
/arena setheight <name> <blocks>   # floor/ceiling distance from the centre Y, default 25
/arena list | delete <name> | tp <name>

/duel <player> [arena]      # clickable accept/deny; random free arena if none given
/duel accept|deny [player]
/duel forfeit
```
Duels save and restore your location, inventory, health, XP and effects, run a
freeze countdown, and end in a draw after `duel.max-duration-seconds`. Duel
wins/losses show in `/stats`. Settings live under `duel:` in `config.yml`.

### Borders (wilderness arenas)
Arenas are just natural terrain, so each one is a square region (centre + radius).
Duelists see a per-player world border, can't walk, get knocked, or teleport out of
it, and can't break/place blocks or start fires (`duel.protect-terrain`), so the
wilderness is left untouched. An arena only counts as ready when both spawns are
inside its border.

### Mobs & height
- **Mobs:** at duel start every mob inside the arena volume is removed silently
  (tamed pets and name-tagged mobs are left alone), new spawns inside an active
  arena are cancelled, and a 2-second sweep catches mobs wandering in
  (`duel.clear-mobs`).
- **Height:** the arena is a box, not just a square. Players can't go above the
  ceiling, and falling below the floor sends them back to their spawn. Both are
  `setheight` blocks above/below the arena centre's Y.

### Spectating
```
/spectate                    # list running duels (or leave, if you're spectating)
/spectate <player|arena>     # watch a duel
/spectate leave
```
Spectators fly in spectator mode inside the arena volume, see the same border, and
can only use `/spectate` and the allowed duel commands. When the duel ends (or they
quit) they're returned to their old location and game mode. Starting a duel
broadcasts a clickable **[watch]**. Settings: `duel.allow-spectators`,
`duel.max-spectators`, `duel.announce-start`. Permission: `mintpvp.spectate`.

## Duels menu, queue & kits
```
/menu  (aliases /duels, /play)   # the hub GUI: queue, ranked (coming soon), challenge, kit layout, spectate, stats
/queue [kit|leave]               # random matchmaking, one queue per kit
/duel [player] [kit] [arena]     # no args opens the challenge GUI
/kit                             # everyone: edit your layout for any kit
```
**Kits (admins)** are captured from your inventory, including armor and offhand, and
stored in `kits.yml`:
```
/kit create <name>      # from the inventory you're carrying
/kit update <name>      # re-capture
/kit seticon <name>     # held item becomes the GUI icon
/kit edit <name>        # arrange the default layout
/kit delete <name>
/kit admin              # the same, as a GUI (also reachable from /menu)
```
**Layouts:** players rearrange the 36 storage slots of a kit in a GUI that looks like
their inventory. Items can only be moved, never added or removed - on save the layout
must contain exactly the kit's items, and a layout that no longer matches (because an
admin changed the kit) falls back to the default.

**Ranked:** the queue type exists but is disabled; the hub shows it as "coming soon".

> `kit.give-on-join` / `kit.give-on-respawn` are **off** by default because giving a kit
> replaces the player's inventory. Duels always save and restore inventories.
