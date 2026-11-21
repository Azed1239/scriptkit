# ScriptKit

A free, open-source (MIT) Fabric mod for scripted Minecraft SMPs: choreographed NPCs and recordable scenes for filming videos on your own server.

**Status:** Phase 0 – empty scaffold that loads. No features yet.

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

With AI on, NPCs wander and look at nearby players (placeholder behaviour). With AI off they stand still and run no goals at all. The state is saved with the NPC.

### Config

`config/scriptkit/config.json`, created on first run and re-read on server start:

```json
{ "maxArmySize": 100, "friendlyFire": false, "defaultFormation": "circle" }
```

`friendlyFire` is applied to teams when they are created; existing teams keep their own setting (`/team modify <team> friendlyFire true`).

## Layout

`dev.scriptkit` – `npc`, `ai`, `command`, `network`, `record`, `platform` (main source set) and `client` (client source set). Version-specific APIs (rendering, payloads, registries) belong in thin wrappers so a later Minecraft 26.x port stays small.

## License

MIT – see [LICENSE](LICENSE).
