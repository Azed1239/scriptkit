# ScriptKit

A free, open-source (MIT) Fabric mod for scripted Minecraft SMPs: choreographed NPCs and recordable scenes for filming videos on your own server.

**Status:** Phase 3 – NPCs, armies and teams, combat AI.

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

`config/scriptkit/kits.json` is created on first start with `knight`, `archer` and `none`. Each kit maps `head`, `chest`, `legs`, `feet`, `mainhand`, `offhand` to item ids; omitted slots are emptied. NPCs drop all equipped gear when they die, like a player. The file is re-read on server start.

## Armies and teams (Phase 2)

| Command | Effect |
|---|---|
| `/army spawn <count> <team> [kit] [skin] [formation]` | Spawn `<count>` NPCs named `<team>-1`, `<team>-2`, ... around you in a `circle` (default) or `grid`. Skin defaults to each NPC's name. Numbering continues from the highest existing number on that team. |
| `/army toggle <team\|all> [on\|off]` | Set `aiEnabled` for a team; without `on`/`off` each NPC's state is flipped. |
| `/army remove <team\|all>` | Remove a team's NPCs. |
| `/army list` | NPC count per team, and how many have AI on. |
| `/army team <selector> <team>` | Move NPCs to a team, e.g. `/army team @e[type=scriptkit:npc,distance=..10] red`. |

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
- **Shield:** with a shield in the offhand it keeps the shield up while an enemy is close, lowers it only to swing, and times its swing to land right after an NPC opponent's attack.
- **Bow:** keeps 10–15 blocks away, draws fully, leads moving targets, and runs from enemies closer than 6 blocks. Arrows are real projectiles (they can be blocked) but consume no ammo and can't be picked up.
- **Idle:** with no target it stands still and looks around or at nearby players. It does not wander, so formations hold.
- **Stuck:** if it can't make progress toward a target it jumps, then repaths, then gives up on that target for 5 seconds.

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
| `dropKitOnDeath` | true | Drop equipped gear on death. |
| `deathMessages` | true | Broadcast death messages (also follows the `showDeathMessages` game rule). |
| `leaveMessages` | true | Broadcast "left the game" after a death. |
| `leaveMessageOnRemove` | false | Also broadcast "left the game" for `/npc remove` and `/army remove`. |

## Layout

`dev.scriptkit` – `npc`, `ai`, `command`, `network`, `record`, `platform` (main source set) and `client` (client source set). Version-specific APIs (rendering, payloads, registries) belong in thin wrappers so a later Minecraft 26.x port stays small.

## License

MIT – see [LICENSE](LICENSE).
