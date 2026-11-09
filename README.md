# ᴍɪɴᴛ ᴘᴠᴘ

A light mint-green, small-caps themed PvP plugin for **Paper 26.3** (Java 25).

## Features
- Mint hex palette + small-caps chat, sidebar, titles (configurable in `config.yml`)
- Kit given on join/respawn (fully configurable), instant respawn, keep-inventory
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
/arena list | delete <name> | tp <name>

/duel <player> [arena]      # clickable accept/deny; random free arena if none given
/duel accept|deny [player]
/duel forfeit
```
Duels save and restore your location, inventory, health, XP and effects, run a
freeze countdown, and end in a draw after `duel.max-duration-seconds`. Duel
wins/losses show in `/stats`. Settings live under `duel:` in `config.yml`.
