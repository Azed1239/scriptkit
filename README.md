# ScriptKit

A free, open-source (MIT) Fabric mod for scripted Minecraft SMPs: choreographed NPCs and recordable scenes for filming videos on your own server.

**Status:** Phase 5 – NPCs, armies and teams, combat AI, keybinds, in-game menu.

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
| `/npc difficulty <name\|all> <level>` | Set one NPC's (or every NPC's) difficulty. |
| `/npc info <name>` | Why is this NPC (not) fighting? Shows its AI state, team and target, and for the 8 nearest living entities whether each is a valid enemy or why it is ignored (same team, creative mode, out of range, no team...). |

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
| `/army spawn <count> <team> [kit] [skin] [formation] [difficulty]` | Spawn `<count>` NPCs named `<team>-1`, `<team>-2`, ... around you in a `circle` (default) or `grid`. Skin defaults to each NPC's name. Numbering continues from the highest existing number on that team. |
| `/army toggle <team\|all> [on\|off]` | Set `aiEnabled` for a team; without `on`/`off` each NPC's state is flipped. |
| `/army remove <team\|all>` | Remove a team's NPCs. |
| `/army list` | NPC count per team, and how many have AI on. |
| `/army team <selector> <team>` | Move NPCs to a team, e.g. `/army team @e[type=scriptkit:npc,distance=..10] red`. |
| `/army difficulty` | List the difficulty presets and what each changes. |
| `/army difficulty <team\|all> <level>` | Set an army's difficulty (also saved with each NPC). |
| `/army kit <team\|all> <kit>` | Give every NPC on a team (or all) a kit. |
| `/scriptkit kit <kit> [players]` | Put a kit's items in your inventory (or other players'), like `/give`. Anything that doesn't fit drops at your feet; the armor you are wearing is never replaced. |
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

- **Targets** (shown by `/npc info`) the nearest living entity on a *different* scoreboard team within `detectionRange`: other NPCs and players. Teammates, spectators, creative players (unless `targetCreativePlayers`) and players without a team are never targeted, so you can walk among armies while filming. Enemies farther than `detectionRange` are not noticed either: when one army wipes out another, the survivors go idle unless another enemy is within range. To join a fight, put yourself on a team with `/team join`.
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

### Difficulty

Every NPC has a difficulty preset, saved with it, so different armies can fight at different levels. New NPCs use `defaultDifficulty` (normal); `/army spawn ... <formation> <difficulty>` sets it at spawn, and `/army difficulty red nightmare` changes a whole army at any time, even mid-fight. `/army list` and `/npc info` show it.

| | easy | normal | hard | nightmare |
|---|---|---|---|---|
| Damage dealt (melee and arrows) | x0.6 | x1.0 | x1.6 | x3.0 |
| Health | x0.8 | x1.0 | x1.3 | x2.0 |
| Melee accuracy | 45% | 75% | 90% | 100% |
| Archer accuracy | 50% | 80% | 92% | 100% |
| Jump crit chance | 10% | 35% | 50% | 70% |
| Swing rate | x0.8 | x1.0 | x1.15 | x1.3 |
| Speed | x1.0 | x1.0 | x1.05 | x1.1 |
| Shield skill | 30% | 70% | 90% | 100% |
| Reaction (how fast they notice and switch enemies) | x0.5 | x1.0 | x1.5 | x2.5 |
| Detection range | x0.8 | x1.0 | x1.2 | x1.5 |

Against a player in full enchanted netherite (the `god` kit) a normal NPC lands hits that do only a few points of damage, so a god-kit player needs `hard` or `nightmare` armies to be in real danger. Presets are plain entries in the `difficulties` section of `combat.json`; edit them or add your own (any name works in the commands). Values are clamped to sane ranges so a typo can't break fights. `damageMultiplier` below still applies on top of the preset.

### Combat config

`config/scriptkit/combat.json`, created on first run and re-read on server start (new options are added to older files automatically):

| Option | Default | Meaning |
|---|---|---|
| `detectionRange` | 32 | Blocks within which NPCs notice enemies. |
| `retargetIntervalTicks` | 20 | How often each NPC re-picks its target. |
| `strafe` | true | Circle-strafe and step back in melee. |
| `block` | true | Use offhand shields. |
| `targetCreativePlayers` | false | Also chase and swing at players in creative mode (they can't be hurt). |
| `defaultDifficulty` | normal | Difficulty for NPCs that don't have one set. |
| `difficulties` | (4 presets) | The presets above: accuracy, crit chance, damage, health, speed and more. |
| `damageMultiplier` | 1.0 | Scales all NPC damage (melee and arrows) on top of the difficulty. Lower = longer fights. |
| `npcsCanHurtPlayers` | true | If false, NPCs never target players. |
| `playerFriendlyFire` | true | Players can damage NPCs on their own team (accidental hits count). |
| `dropKitOnDeath` | true | Drop equipped gear on death. |
| `deathMessages` | true | Broadcast death messages (also follows the `showDeathMessages` game rule). |
| `leaveMessages` | true | Broadcast "left the game" after a death. |
| `leaveMessageOnRemove` | false | Also broadcast "left the game" for `/npc remove` and `/army remove`. |
| `allowBlockBreaking` | true | Stuck NPCs may mine blocks. |
| `allowBlockPlacing` | true | Stuck NPCs may pillar up with blocks. |
| `maxBreakHardness` | 5.0 | Hardest block NPCs may break (stone 1.5, iron block 5, obsidian 50). |
| `pillarBlock` | `minecraft:cobblestone` | Block used for pillaring. |
| `dropMinedBlocks` | false | Mined blocks drop as items. |

## Menu (Phase 5)

Press **K** (rebindable in Controls > ScriptKit) or run `/scriptkit menu` to open the ScriptKit menu. You still create armies and NPCs with commands; the menu is for tuning and equipping them. It needs ScriptKit on your client; changing anything on the server needs operator (gamemaster) permission, otherwise those tabs are read-only.

| Tab | What you can do |
|---|---|
| **Difficulty** | Pick a preset and tune every value with sliders (damage, health, accuracies, crit chance, swing rate, speed, shield skill, reaction, range). Save it, save a copy under a new name, reset a built-in preset or delete a custom one, make it the default for new NPCs, and apply it to an army. |
| **Kits** | Pick a kit and set each of the six slots with the item picker (search by name or id, with icons). Left-click an enchantment to raise its level, right-click to lower it. Save, save as a new kit, reset a built-in kit or delete a custom one, and give the kit to an army, to all NPCs, to one NPC by name (pick "one NPC by name" in the Give to selector and type the name), or to yourself (pick "me": the items go in your inventory). |
| **Keybinds** | Edit your key combos without a text editor: choose the action and team, click the key box and press the combo, then Save & reload. Entries the editor doesn't understand are kept untouched. A button opens the vanilla Controls screen for the G key, the team slots and the menu key. This tab only changes your own `keybinds.json`. |

Edits are validated on the server (items and enchantments must exist, levels 1-255, names use letters, digits, `-` and `_`) and saved to `combat.json` and `kits.json`, so they survive restarts and can still be hand-edited.

## Names and skins

Armies get procedural gamer-tag names like `PingAbuse`, `BanThis`, `SwordPro` or `xXDarkLordXx` (3-16 characters, unique among NPCs, usable in commands). Set `nameStyle` to `numbered` in `config/scriptkit/config.json` to get `red-1`, `red-2` again. Names show on name tags and in death messages.

Army NPCs without a skin you chose get a random built-in skin (Steve, Alex, Ari, Efe, Kai, Makena, Noor, Sunny, Zuri, in both arm shapes), picked per NPC and stable across restarts, with no account lookups. Give a skin to `/army spawn <count> <team> <kit> <skin>` to override it for the whole army, or list usernames in `skinPool` (config.json) to pick randomly from real accounts. `/npc spawn <name>` still uses the name as the skin unless you pass one.

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

- `./gradlew test` runs unit tests for key-combo matching, `keybinds.json` parsing, key names and the name generator.
- Starting the client with `-Dscriptkit.debug.ui=true` runs a UI smoke test: it opens the menu on the title screen, shows every tab and the item picker, saves screenshots to `run/screenshots`, and quits.
- With `-Dscriptkit.debug=true` the dev server also gets `/scriptkit simulate-menu <op|denied> <action> <json>` and `/scriptkit snapshot`.
- Starting a dev server with `-Dscriptkit.debug=true` adds `/scriptkit simulate <sender> <op|denied> <action> [team]`, which runs the keybind packet handler without a client.

## Layout

`dev.scriptkit` – `npc`, `ai`, `command`, `network`, `record`, `platform` (main source set) and `client` (client source set). Version-specific APIs (rendering, payloads, registries) belong in thin wrappers so a later Minecraft 26.x port stays small.

## License

MIT – see [LICENSE](LICENSE).
