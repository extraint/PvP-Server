# ᴍɪɴᴛ ᴘᴠᴘ

A light mint-green, small-caps themed PvP plugin for **Paper 26.3** (Java 25).

## Features
- Mint hex palette + small-caps chat, sidebar, titles (configurable in `config.yml`)
- Admin-made kits, editable per-player layouts, random duel queue, custom mint GUIs
- Instant respawn, keep-inventory
- Combat tag with action-bar timer, command blocking, combat-log punishment
- Persistent kills / deaths / K/D / kill streaks, streak announcements. Duel, party and free-for-all eliminations count: the last opponent to hit you is credited with the kill (projectiles too), you get a death, and quitting mid-match counts as a death
- Arenas + 1v1 duels, party duels and party free-for-alls (see below)
- `/stats [player]`, `/leaderboard [tier|wins|kills|kd|streak]`, `/kit`, `/mintpvp reload`

## Build
```bash
mvn package   # needs JDK 25; jar ends up in target/
```
Drop `target/MintPvP-1.0.0.jar` into your server's `plugins/` folder.

## Permissions
- `mintpvp.admin` – reload and tier commands (default: op)
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
freeze countdown, and have **no time limit**. Duel
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
/duel [player] [kit] [rounds] [arena]     # no args opens the challenge GUI
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


## Ranked (kit tiers + score)
`/queue ranked [kit]` or the diamond sword. There is no Elo: every kit has its own **tier**, and your
overall **tier** is worked out from the points of your tier in each kit added together.

| Tier | LT5 | HT5 | LT4 | HT4 | LT3 | HT3 | LT2 | HT2 | LT1 | HT1 |
|---|---|---|---|---|---|---|---|---|---|---|
| Points | 3 | 8 | 12 | 20 | 28 | 38 | 50 | 65 | 92 | 110 |

How tiers move (per kit, all numbers under `ranked:` in `config.yml`):
- your **first ranked game** in a kit places you at **LT5** (unranked = 0 points in that kit)
- a **win** over someone at your tier or higher is +1 progress (+2 over someone 2+ tiers above, and +1 extra
  from a 3-win streak); **2 progress promotes** you (`ranked.wins-to-promote`)
- beating someone *below* your tier gives no progress, so farming low tiers doesn't pay
- a **loss** to someone at your tier or lower is -1 progress; **2 demotes** you (never below LT5).
  Losing to a higher tier costs nothing. Draws change nothing.
- quitting mid-duel is a loss; a server shutdown voids the match
- matchmaking pairs players with the closest tier **in that kit**, in a window that starts at 1 tier and widens
  by 1 every 10 s of waiting

Overall tier titles (by total points): **unranked** 0, **rookie** 3-9, **novice** 10-24, **cadet** 25-49,
**specialist** 50-99, **ace** 100-199, **master** 200-349, **grandmaster** 350+. They show as e.g. `tier: 86 · specialist`.

After each ranked duel you see the promotion/demotion (or your progress) and your new overall tier.
`/stats` lists your tier in every kit you've played. `/leaderboard [tier|wins|kills|kd|streak]` or the GUI.
Admins (`mintpvp.admin`) set tiers with `/mintpvp tier set <player> <kit|all> <lt5..ht1|unranked>`,
`/mintpvp tier reset <player> [kit|all]` and `/mintpvp tier info <player>`. Tier points are defined in `util/Tier.java`. (Old Elo values in `stats.yml` are ignored.)

## Rounds ("first to")
`/duel <player>` no longer starts a classic duel straight away: it opens a **kit menu**, then a **rounds menu**
(single round, first to 2, 3, 5, 7, 10), then sends the request. The challenge picker in the hub and hotbar does
the same. Shortcuts: `/duel <player> <kit>` skips to the rounds menu, and `/duel <player> <kit> <rounds>` sends it
straight away.
- The first player to win N rounds wins the match. After each round there is a short pause
  (`duel.round-delay-seconds`), then the arena's changed blocks are restored, both players get a fresh kit and
  are put back at their spawns for a countdown (`duel.round-countdown-seconds`).
- There is no time limit. Quitting between or during rounds forfeits the match.
- Ranked tiers and duel wins/losses count once, for the whole match; kills and deaths count every round.
- **Ranked is always first to 3** (`ranked.first-to`): tier queue matches and `/challenge` skip the rounds menu.
- Options: `duel.round-options`, `duel.max-rounds`. Rounds apply to 1v1 duels (not parties; the unranked queue is a single round).

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
- No visible walls and no ground decorations: an invisible barrier seals the edge. Cover is only trees, dunes and snow drifts: no walls, boulders, pillars, ice spikes or hay bales, and no surface shafts. When the arena or lobby design changes, the cached arena chunks are cleared once on the next start (`DESIGN_VERSION` in `ArenaGenerator`).
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
  kit tier. `/challenge` alone opens a picker, and the hub and hotbar have a ranked-challenge button
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
Winner = the other player dies, quits or forfeits. There is no time limit. A draw only happens if the
server restarts, an error stops the duel, or everyone dies at once, and the message
says which ("the duel ended in a draw: ..."). Every result is also logged in the server console.

## Parties, party duels and party free-for-all
All three run on the same match engine as 1v1 duels (same arenas, countdown, kits, spectating, reset).
A player who dies is eliminated and watches from spectator mode where they fell; the match ends when one
team is left. Ranked tiers only apply to 1v1 ranked duels.

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
5+ players it uses a big arena. There is no public free-for-all queue.

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
The generated lobby is a large floating quartz plaza (80 blocks across): a three-step **terraced dais** (with
stairs) holding a lit fountain, an emerald pedestal and a **beacon** with a lime beam, ringed by a flowering
hedge with benches; mint-teal rings in the wedge-patterned floor, lamp posts, flower planters, three crystal
spires, four themed gardens (poplar, cherry, snowy, desert), a huge **MINT PVP** sign (dark teal panel, glowing sea-lantern frame, lime letters with a drop shadow) with a flower bed
at its foot, a railed edge with an invisible barrier above it, a tapering underside, and floating islets in the
sky. You spawn on the south side, facing the dais and the sign. It lives in the arena world at x=-2400; servers
with an older lobby are moved to this one on start (a spawn you set elsewhere with `/setspawn` is left alone).

**The lobby can't be changed by players.** Nobody can break, place, burn, flood, melt, blow up or trample anything
there, take items out of frames or stands, or empty/fill buckets. Not even ops: to build you must turn on
**builder mode** with `/build` (needs `mintpvp.build`, ops have it by default); it's off by default and turns
itself off when you leave.
