# Mint PvP test bot

A headless Minecraft client (built on [Mineflayer](https://github.com/PrismarineJS/mineflayer))
that joins your server, queues and fights, so you can test queues, duels and Elo on your own.

```bash
cd tools/testbot
npm install
node bot.js --host localhost --port 25565 --name BotA --mode ranked --passive
node bot.js --host localhost --port 25565 --name BotB --mode ranked
```

`BotA` never fights back, `BotB` attacks, so BotB wins and you can watch the Elo change.
Run only one bot and join yourself to duel it.

| Flag | Meaning |
|---|---|
| `--mode ranked\|unranked` | join that queue on spawn (default `ranked`) |
| `--mode accept` | wait in the lobby and auto-accept any duel request |
| `--mode challenge:<name>` | send `/duel <name> [kit]` on spawn |
| `--kit <name>` | kit to queue / challenge with (default `classic`) |
| `--passive` | never attack |
| `--requeue` | queue again after every duel |
| `--version <v>` | client protocol version, see below |

## Requirements - read this

- The server must be in **offline mode** (`online-mode=false` in `server.properties`), otherwise
  the bot can't log in. Only do this on a test server.
- You need **at least one arena** (`/arena create` ...) and two players in the queue.
- **Version support:** Mineflayer currently knows Minecraft up to **26.1**, but Paper **26.3**
  speaks a newer protocol (777 vs 775), so the bot is rejected as an "outdated client". To use
  it on a 26.3 test server you need a protocol translator such as
  [ViaVersion](https://modrinth.com/plugin/viaversion) installed on that server, then run the bot
  with `--version 26.1`. Alternatively wait for Mineflayer to add 26.3 support.

Status: this bot has **not** been run against a server yet (the version gap above stopped the
test); the plugin itself has only been boot-tested.
