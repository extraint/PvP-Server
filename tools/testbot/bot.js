#!/usr/bin/env node
/*
 * Mint PvP test bot.
 *
 *   npm install
 *   node bot.js --host localhost --port 25565 --mode ranked --kit classic
 *
 * Modes:
 *   ranked | unranked   join that queue on spawn (and again after every duel with --requeue)
 *   accept              wait in the lobby and auto-accept any duel request
 *   challenge:<name>    send /duel <name> [kit] on spawn
 *
 * Flags: --passive (never attacks, so you can win), --requeue, --name, --version, --kit
 * The server must be in offline mode (online-mode=false) for a bot to join.
 */
const mineflayer = require('mineflayer');

const argv = process.argv.slice(2);
const opt = (name, def) => {
  const i = argv.indexOf('--' + name);
  return i >= 0 && argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[i + 1] : def;
};
const flag = (name) => argv.includes('--' + name);

const host = opt('host', 'localhost');
const port = parseInt(opt('port', '25565'), 10);
const name = opt('name', 'MintBot');
const version = opt('version', undefined); // e.g. 26.1; omit to auto-detect
const mode = opt('mode', 'ranked');
const kit = opt('kit', 'classic');
const passive = flag('passive');
const requeue = flag('requeue');

// the plugin writes in small caps; map it back to ascii so we can read messages
const SMALL = 'ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ';
const unsmall = (s) => [...s].map((c) => { const i = SMALL.indexOf(c); return i >= 0 ? String.fromCharCode(97 + i) : c; }).join('');

const bot = mineflayer.createBot({ host, port, username: name, version, auth: 'offline' });
const log = (...a) => console.log(`[${name}]`, ...a);

let queued = false;
function joinQueue() {
  if (mode === 'ranked' || mode === 'unranked') {
    log(`/queue ${mode} ${kit}`);
    bot.chat(`/queue ${mode} ${kit}`);
    queued = true;
  } else if (mode.startsWith('challenge:')) {
    const target = mode.split(':')[1];
    log(`/duel ${target} ${kit}`);
    bot.chat(`/duel ${target} ${kit}`);
  }
}

bot.once('spawn', () => {
  log('spawned at', bot.entity.position.floored().toString());
  setTimeout(joinQueue, 3000);
});

bot.on('messagestr', (raw) => {
  const msg = unsmall(raw);
  log('<<', msg);
  if (/challenged you/.test(msg)) {
    log('accepting duel');
    bot.chat('/duel accept');
  }
  if (requeue && /defeated|ended in a draw/.test(msg)) {
    setTimeout(joinQueue, 6000);
  }
});

bot.on('kicked', (r) => log('kicked:', r));
bot.on('error', (e) => log('error:', e.message));
bot.on('end', (r) => { log('disconnected:', r); process.exit(0); });
bot.on('death', () => log('died'));

// very small fighter: walk at the nearest player and hit them
let lastHit = 0;
setInterval(() => {
  if (!bot.entity || passive) return;
  const target = bot.nearestEntity((e) => e.type === 'player' && e.username !== name);
  if (!target) { bot.clearControlStates(); return; }
  const dist = bot.entity.position.distanceTo(target.position);
  if (dist > 40) { bot.clearControlStates(); return; }
  bot.lookAt(target.position.offset(0, target.height * 0.9, 0), true);
  bot.setControlState('forward', dist > 2.5);
  bot.setControlState('sprint', dist > 4);
  if (dist <= 3.2 && Date.now() - lastHit > 550) {
    lastHit = Date.now();
    bot.attack(target);
  }
}, 100);

process.on('SIGINT', () => { bot.quit(); setTimeout(() => process.exit(0), 300); });
