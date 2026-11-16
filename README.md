# ᴍɪɴᴛ ᴘᴠᴘ

A light mint-green, small-caps themed PvP plugin for **Paper 26.3** (Java 25).

## Features
- Mint hex palette + small-caps chat, sidebar, titles (configurable in `config.yml`)
- Admin-made kits, editable per-player layouts, random duel queue, custom mint GUIs
- Instant respawn, keep-inventory
- Combat tag with action-bar timer, command blocking, combat-log punishment
- Persistent kills / deaths / K/D / kill streaks, streak announcements
- Arenas + 1v1 duels, party duels and party free-for-alls (see below)
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
/arena check <name>         # explains exactly what's wrong with an arena (also works from the console)
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


## Ranked (Elo)
`/queue ranked [kit]` or the diamond sword. Everyone starts at **1000 elo** (silver).
Matches pair the closest ratings inside a window that starts at ±100 and widens by 50
every 10 s of waiting (max ±800). Results use standard Elo with `ranked.k-factor` (32);
a win is always at least +1, ratings never go below 0, quitting mid-duel is a loss, and a
server shutdown voids the match. Tiers (by Elo): LT5 (0), HT5 (850), LT4 (1000, where everyone starts), HT4 (1150), LT3 (1300),
HT3 (1450), LT2 (1600), HT2 (1750), LT1 (1900), HT1 (2050). They're defined in
`util/Tier.java` if you want to move the cut-offs.
`/leaderboard [elo|wins|kills|kd|streak]` or the GUI.

## Spawn & lobby
```
/setspawn     # admin: sets the lobby spawn AND the world spawn
/spawn        # (aliases /lobby, /hub) back to spawn, not during duels or combat
```
Players are sent to spawn on join and respawn, and get a hotbar menu (right-click):
iron sword = unranked queue, diamond sword = ranked queue, bow = challenge,
nether star = menu, book = stats, ender chest = kit layout, gold = leaderboard,
ender eye = spectate. While queued the sword turns into a **leave queue** button.
Lobby players take no damage or hunger and can't build, drop or move items
(`mintpvp.build` exempts ops). Config: `lobby.give-items`, `lobby.protect`, `spawn.*`.

> `lobby.give-items` **replaces the player's inventory** on join/respawn - turn it off on a
> survival world.

> `kit.give-on-join` / `kit.give-on-respawn` are **off** by default because giving a kit
> replaces the player's inventory. Duels always save and restore inventories.

### Arena troubleshooting
`/arena list` shows every arena's status and `/arena check <name>` says what's wrong
(spawn missing, world not loaded, different worlds...). If a duel fails to start, the
error is printed in the console and both players are put back where they were. The border
always grows to include both spawns, so a spawn can't end up outside its own arena. Arenas
are stored by world name, so an arena in a world that isn't loaded yet is skipped (not lost)
until that world loads.

## Pre-made arenas (auto-generated)
The plugin generates its own arena world (`mint_arenas_2`) the way a normal world is made: terrain
appears as chunks load, so there is nothing to build and no waiting. No other world is touched.
Five themes: **Plains, Desert, Poplar Grove, Snowy, Cherry Grove**.

- **Underground:** every arena goes down to bedrock with stone, deepslate, granite/diorite/andesite/tuff
  patches, ores, winding tunnels, big caverns and a few cave entrances from the surface. Everything
  is mirror-symmetric, so both players always get the same ground. Players can dig; see "Reset".
- **Unranked arenas:** 75 blocks across, hand-designed cover, spawns 60 apart. 2 of each theme on start.
- **Ranked arenas:** the same themes at a random radius of **100-150** (200-300 across) with rolling
  hills and scattered cover; spawns are 80% of the radius from the centre. 1 of each theme on start.
  Ranked queue matches and `/challenge` duels use these; unranked uses the small ones.
- No visible walls and no ground decorations: an invisible barrier seals the edge.
- More arenas are made on demand when all are busy, up to `arena-generator.max-total` (30).
- The **locator bar** is on in the arena world, so players can see each other.

```
/arena themes                                 # themes, how many of each, generator status
/arena generate <theme|random> [count] [large]  # make more ("large" = ranked size)
/arena reset <name>                           # reset a generated arena to its original state
/arena lobby                                  # make the generated lobby the server spawn
```
Settings: `arena-generator:` in `config.yml` (`world-name`, `per-theme`, `ranked-per-theme`,
`ranked-radius-min/max`, `max-total`, `lobby`). Upgrading from the older floating-island arenas:
the old generated arenas are dropped automatically and new ones are made in the new world; you can
delete the old `mint_arenas` world folder.

### Reset
After every duel the arena is **reset completely**. The plugin compares each loaded chunk of the
arena with what the generator would produce (in the background) and puts back every block that
differs, a few thousand per tick, so placed/broken blocks, fire, liquids, falling blocks and
explosions are all undone, and loose items and arrows are cleared. The arena stays locked until
the reset is finished. Hand-made arenas only get back the blocks that were recorded as changed. If
the server stops mid-duel, generated arenas are reset on the way out.

### Building and height in arenas
- Duelists **can place and break blocks** in arenas (`duel.allow-building`, default on). Every
  change is recorded and the arena is put back exactly as it was when the duel ends. Nothing
  can be built or broken on or beyond the border, loot doesn't drop from broken blocks, and
  fire, flowing liquid and explosions are recorded and restored too.
- There is **no height limit** by default (`duel.height-limit: false`), so players can build up
  as high as the world allows. Falling below the world returns them to their spawn. Set
  `duel.height-limit: true` to bring the old floor and ceiling back.

### Ranked challenges and the end of a duel
- `/challenge <player> [kit]` (alias `/ranked`) sends a **ranked** duel request; the other player
  accepts with the clickable button or `/duel accept`, and the result changes both players'
  Elo. `/challenge` alone opens a picker, and the hub and hotbar have a ranked-challenge button
  (crossbow). `/duel` stays unranked. To stop win-trading, the same two players can play at
  most `ranked.challenge-limit-per-hour` (5) ranked challenge duels an hour.
- When someone dies the duel doesn't cut away at once: for `duel.end-delay-seconds` (4) the
  winner gets a victory title, the loser a defeat title and watches from spectator mode, nobody
  can be hurt, and then everyone is sent back. Quitting or forfeiting still ends it straight away.

### Pearls and walls
Ender pearls and chorus fruit work in duels as long as you land inside the arena (a pearl that
would land outside is cancelled). Generated arenas have no visible wall: an invisible barrier
seals the edge.

### How a duel can end
Winner = the other player dies, quits or forfeits. At the time limit (`duel.max-duration-seconds`,
300) the player with more health wins (`duel.timeout-winner-by-health`); only an exact tie is a
draw. A draw can also happen if the server restarts or an error stops the duel, and the message
says which ("the duel ended in a draw: ..."). Every result is also logged in the server console.

## Parties, party duels and party free-for-all
All three run on the same match engine as 1v1 duels (same arenas, countdown, kits, spectating, reset).
A player who dies is eliminated and watches from spectator mode where they fell; the match ends when one
team is left. Ranked Elo only applies to 1v1 ranked duels.

### Parties
```
/party                         # the party menu (create, members, invite, challenge, leave)
/party create | invite <player> | accept|deny [leader] | leave | list
/party kick <player> | promote <player> | disband      # leader only
/party duel <player> [kit]     # leader: challenge the party that player is in (they answer with /duel accept)
/pc <message>                  # party chat
```
Up to `party.max-size` (6) players. Inviting someone makes you a party. A party duel is team vs team
on the same kit; parties can be any size (2v1, 3v1, 3v2...; set `party.allow-uneven-teams: false` to require equal sizes), nobody may be busy,
teammates can't hurt each other (`party.friendly-fire`), and 7+ players total use a big arena.
Leaders can also challenge from the menu. Parties live in memory (they end on restart or logout).

### Party free-for-all
A party leader can start a free-for-all **inside their own party**: `/party ffa [kit]` or the party menu's TNT
button (needs `party.ffa-min-players`, 3). Everyone fights everyone and the last player standing wins; with
5+ players it uses a big arena, and at `party.ffa-max-duration-seconds` (420) the player with the most health
wins. There is no public free-for-all queue.

### Custom teams and party rules
- **Custom teams:** in the party menu a leader can click **custom teams** (banner) to split their own party
  into a red and a blue team of any sizes by clicking players, then pick a kit and start (2v1, 3v2...).
  A solo player makes a one-person party to be the "1" in a 3v1 against someone else's party.
- **Lobby hotbar:** slot 6 (firework rocket) opens the party menu, so you can make and manage a party
  right from the lobby.
- **In a party you can only do party things:** while you're in a party you can't join a queue (ranked or
  unranked), start or accept a 1v1 duel or ranked challenge, or spectate. Leave the
  party first (`/party leave`). Kit layouts, stats and the leaderboard still work.

## The lobby
The generated lobby is a large floating quartz plaza (80 blocks across): a lit fountain monument in the
middle, lime spokes and rings in a wedge-patterned floor, four small gardens (poplar, cherry, snowy,
desert), lamp posts, a big lime **MINT PVP** sign on a white wall, a railed edge with an invisible
barrier above it, and a tapering underside. You spawn just south of the fountain, facing it and the sign.
It lives in the arena world at x=-1400; servers with the older lobby are moved to this one on start
(a spawn you set elsewhere with `/setspawn` is left alone).
