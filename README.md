# ScriptKit

A free, open-source (MIT) Fabric mod for scripted Minecraft SMPs: choreographed NPCs and recordable scenes for filming videos on your own server.

**Status:** Phase 4 – NPCs, armies and teams, combat AI, keybinds.

## Target

| | |
|---|---|
| Minecraft | 1.21.11 (obfuscated; Mojang mappings) |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.141.6+1.21.11 |
| Loom | `net.fabricmc.fabric-loom-remap` 1.19.4 |
| Java | 21 |

## Building

```
./gradlew build        # jar in build/libs/
./gradlew runServer    # dev server (accept EULA in run/eula.txt first)
./gradlew runClient    # dev client
```

## NPCs (Phase 1)

NPCs are lightweight entities rendered like players with a skin fetched by username (default skin while loading or if the lookup fails). They stand still and never despawn.

| Command | Effect |
|---|---|
| `/npc spawn <name> [skin] [team] [kit]` | Spawn at your position. Skin defaults to the name; team is a vanilla scoreboard team (created if missing); kit defaults to `none`. |
| `/npc remove <name\|all>` | Remove NPCs. |
| `/npc list` | List NPCs with skin, team, kit and position. |
| `/npc kit <name\|all> <kit>` | Change gear. |

All commands need op (gamemaster) permission. NPC names are unique (case-insensitive) among loaded NPCs; `all` is reserved. Commands only see NPCs in loaded chunks.

### Kits

Built-in kits: `knight` (iron, sword, shield), `archer` (leather, bow), `god` (maxed-out enchanted netherite with a netherite sword and shield) and `none`. They're always available; `config/scriptkit/kits.json` (written on first start) can override them or add your own. Each kit maps `head`, `chest`, `legs`, `feet`, `mainhand`, `offhand` to an item id, or to an object with enchantments:

```json
"mainhand": { "id": "minecraft:netherite_sword", "enchantments": { "minecraft:sharpness": 5, "minecraft:fire_aspect": 2 } }
```

Omitted slots are emptied. NPCs drop all equipped gear when they die, like a player. The file is re-read on server start.

## Armies and teams (Phase 2)

| Command | Effect |
|---|---|
| `/army spawn <count> <team> [kit] [skin] [formation]` | Spawn `<count>` NPCs named `<team>-1`, `<team>-2`, ... around you in a `circle` (default) or `grid`. Skin defaults to each NPC's name. Numbering continues from the highest existing number on that team. |
| `/army toggle <team\|all> [on\|off]` | Set `aiEnabled` for a team; without `on`/`off` each NPC's state is flipped. |
| `/army remove <team\|all>` | Remove a team's NPCs. |
| `/army list` | NPC count per team, and how many have AI on. |
| `/army team <selector> <team>` | Move NPCs to a team, e.g. `/army team @e[type=scriptkit:npc,distance=..10] red`. |
| `/army blocks [on\|off]` | Turn NPC mining and building on or off (saved to `combat.json`). Without an argument, shows the current state. |
| `/army cleanup` | Put back every block NPCs mined or placed. Blocks changed since by someone else are left alone. |

Teams are vanilla scoreboard teams. When the mod creates one it gets a stable color (change it with `/team modify <team> color <color>`) and the friendly-fire flag from the config. `all` is reserved and cannot be a team name.

With AI on, NPCs fight (see Combat below). With AI off they stand still and run no goals or target scans at all. The state is saved with the NPC.

### Config

`config/scriptkit/config.json`, created on first run and re-read on server start:

```json
{ "maxArmySize": 100, "friendlyFire": false, "defaultFormation": "circle" }
```

`friendlyFire` is applied to teams when they are created. NPC damage between teammates needs both this flag and the team's own setting to be on, so the default (`false`) always protects teammates, even on teams created before the config existed.

## Combat (Phase 3)

With AI on, each NPC:

- **Targets** the nearest living entity on a *different* scoreboard team within `detectionRange`: other NPCs and players. Teammates, spectators, creative players and players without a team are never targeted, so you can walk among armies while filming. To join a fight, put yourself on a team with `/team join`.
- **Melee:** chases with vanilla pathfinding (sprinting when far), swings at the weapon's real attack speed (fists fast, swords medium, axes slow), and uses the weapon's damage and knockback. In melee range it circle-strafes, changing direction at random, and sometimes steps back after being hit.
- **Critical hits:** sometimes (`critChance`) they hop just before a swing and strike on the way down, like a player's jump-crit: 1.5x damage, crit particles and sound.
- **Shield:** with a shield in the offhand it keeps the shield up while an enemy is close, lowers it only to swing, and times its swing to land right after an NPC opponent's attack.
- **Bow:** keeps 10–15 blocks away, draws fully, leads moving targets, and runs from enemies closer than 6 blocks. Arrows are real projectiles (they can be blocked) but consume no ammo and can't be picked up.
- **Idle:** with no target it stands still and looks around or at nearby players. It does not wander, so formations hold.
- **Stuck:** if it can't make progress toward a target it jumps, then repaths, then mines and builds its way there (below), and only then gives up on that target for 5 seconds.

### Mining and building

When normal pathfinding can't reach the target (trapped in a hole, sealed in, target up on a ledge), NPCs work their way out like a player:

- **Pillar up** by jumping and placing a block underneath (cobblestone by default, unlimited supply).
- **Tunnel** through blocks in the way, and **staircase down** toward targets below.
- They briefly show the right tool (or the block) in hand, swing, and blocks crack and break in vanilla time for an iron tool.
- Never broken: blocks with storage or data (chests, beds, signs...), unbreakable blocks, anything harder than `maxBreakHardness` (default 5, so obsidian is safe), and blocks next to water or lava.
- Mined blocks don't drop items unless `dropMinedBlocks` is on.
- Every change is logged (saved with the world) so `/army cleanup` can undo it after a take.

`/army spawn` shows a chat hint with a click-to-toggle button and a pointer to `/army cleanup`.

When an NPC dies, chat shows a player-style death message (for example `red-3 was slain by blue-7`) and a yellow `red-3 left the game`. It drops its kit unless `dropKitOnDeath` is off.

Target scanning is throttled: one shared list of combat-relevant entities per world, rebuilt every 10 ticks, and each NPC re-picks its target every `retargetIntervalTicks`, staggered by entity id.

### Combat config

`config/scriptkit/combat.json`, created on first run and re-read on server start (new options are added to older files automatically):

| Option | Default | Meaning |
|---|---|---|
| `detectionRange` | 32 | Blocks within which NPCs notice enemies. |
| `retargetIntervalTicks` | 20 | How often each NPC re-picks its target. |
| `strafe` | true | Circle-strafe and step back in melee. |
| `block` | true | Use offhand shields. |
| `archerAccuracy` | 0.8 | 0 = wild, 1 = perfect. |
| `damageMultiplier` | 1.0 | Scales all NPC damage (melee and arrows). Lower = longer fights. |
| `npcsCanHurtPlayers` | true | If false, NPCs never target players. |
| `playerFriendlyFire` | true | Players can damage NPCs on their own team (accidental hits count). |
| `critChance` | 0.35 | Chance a melee NPC goes for a jump-crit before a swing. 0 turns crits off. |
| `dropKitOnDeath` | true | Drop equipped gear on death. |
| `deathMessages` | true | Broadcast death messages (also follows the `showDeathMessages` game rule). |
| `leaveMessages` | true | Broadcast "left the game" after a death. |
| `leaveMessageOnRemove` | false | Also broadcast "left the game" for `/npc remove` and `/army remove`. |
| `allowBlockBreaking` | true | Stuck NPCs may mine blocks. |
| `allowBlockPlacing` | true | Stuck NPCs may pillar up with blocks. |
| `maxBreakHardness` | 5.0 | Hardest block NPCs may break (stone 1.5, iron block 5, obsidian 50). |
| `pillarBlock` | `minecraft:cobblestone` | Block used for pillaring. |
| `dropMinedBlocks` | false | Mined blocks drop as items. |

## Keybinds (Phase 4)

Toggle armies with a key instead of typing `/army toggle`. You need operator (gamemaster) permission, the same as the commands, and ScriptKit on both your client and the server (singleplayer and LAN count: the integrated server does the work). Servers without ScriptKit on your client still work fully with commands.

**Controls screen** (Options > Controls > ScriptKit, rebindable like any key; changes persist):

| Key | Default | Does |
|---|---|---|
| Toggle all armies | `G` | Flips AI for every NPC: if any are on they all go off, otherwise all go on. |
| Toggle team 1 / 2 / 3 | unbound | Same, for the teams named in `teamSlots` (see below). |

**Combos** go in `config/scriptkit/keybinds.json`, created on first start with *example* bindings (Ctrl+R red, Ctrl+B blue) that you can edit or delete:

```json
{
  "teamSlots": ["red", "blue", "green"],
  "bindings": [
    { "action": "toggleTeam", "team": "red",  "keys": ["LEFT_CONTROL", "R"] },
    { "action": "disableAll",                  "keys": ["CTRL", "SHIFT", "1"] }
  ]
}
```

- **Actions:** `toggleAll`, `toggleTeam`, `enableAll`, `disableAll`, `enableTeam`, `disableTeam`. The explicit enable/disable ones are handy for filming. Team actions need a `"team"`.
- **Keys:** `A`-`Z`, `0`-`9`, `F1`-`F12`, `SPACE`, `TAB`, arrows, and so on. `CTRL`, `SHIFT` and `ALT` accept either side; `LEFT_CONTROL`, `RIGHT_SHIFT` etc. accept one side only.
- **Exact matching:** Ctrl+R fires only the Ctrl+R binding. Plain `R` does nothing, and so does Ctrl+Shift+R. Keys no binding uses (like W) are ignored, so you can fire a combo while walking. The vanilla `G` binding never fires while Ctrl, Shift or Alt is held.
- **Once per press:** holding the keys doesn't repeat. Nothing fires while chat or another screen is open.
- **Feedback:** the server answers on the action bar with the real result, for example `Red army: ON (30 NPCs)`, or says why nothing happened (no permission, unknown team, no NPCs there).
- **Bad entries:** a binding with an unknown action, key or team name is skipped with a clear warning in the game log; the rest still load.
- `teamSlots` names the teams for the three "Toggle team" keys (the Controls entries are labelled team 1-3).
- **Reload** after editing: `/scriptkit reload` (op). It reloads the server configs and tells every connected client that has ScriptKit to re-read its own `keybinds.json`. On a dedicated server each player edits their own file; the server never reads it.
- The first time you use `/army toggle` in a session, chat mentions the keybind.
- The server ignores more than 4 keybind packets per second per player.

## Development

- `./gradlew test` runs unit tests for the key-combo matching and `keybinds.json` parsing.
- Starting a dev server with `-Dscriptkit.debug=true` adds `/scriptkit simulate <sender> <op|denied> <action> [team]`, which runs the keybind packet handler without a client.

## Layout

`dev.scriptkit` – `npc`, `ai`, `command`, `network`, `record`, `platform` (main source set) and `client` (client source set). Version-specific APIs (rendering, payloads, registries) belong in thin wrappers so a later Minecraft 26.x port stays small.

## License

MIT – see [LICENSE](LICENSE).
