# Changelog

## [Unreleased]

### Changed
- NPC walking speed now matches a walking player (about 4.3 blocks/s; was roughly a quarter of that). Movement-speed attribute is 0.33 and the wander goal uses speed modifier 1.0. Already-spawned NPCs keep their old saved speed; respawn them.

### Added
- Phase 2: `/army spawn <count> <team> [kit] [skin] [formation]` (circle or grid, NPCs named `<team>-N`), `/army toggle <team|all> [on|off]`, `/army remove <team|all>`, `/army list`, `/army team <selector> <team>`.
- `ArmyManager` (npc package) holding all army/team operations, callable from future network code.
- `config/scriptkit/config.json`: `maxArmySize` (100), `friendlyFire` (false), `defaultFormation`.
- Scoreboard teams created by the mod get a stable color and the configured friendly-fire flag; damage between same-team NPCs is blocked when friendly fire is off.
- Placeholder AI (wander, look at players, look around, float) in `ai/NpcGoals`, active only while `aiEnabled` is true. Turning AI off removes all goals so disabled NPCs cost no goal ticking. `aiEnabled` persists in NBT.
- NPCs always drop all equipped gear on death (100% per slot) instead of vanilla's random ~8.5%.
- Phase 1: lightweight NPC entity `scriptkit:npc` (a `PathfinderMob`, not a fake player): 20 health, player-sized hitbox, never despawns, stands still. NBT: `NpcName`, `SkinName`, `TeamName`, `Kit`, `AiEnabled` (reserved for phase 2).
- Client renderer drawing NPCs with the vanilla player model (wide and slim arms), armor and held items, using skins looked up by username through vanilla's async skin cache (default skin while loading or on failure) and a visible name tag.
- Commands (op / gamemaster): `/npc spawn <name> [skin] [team] [kit]`, `/npc remove <name|all>`, `/npc list`, `/npc kit <name|all> <kit>`. Teams are vanilla scoreboard teams, created if missing.
- Gear kits from `config/scriptkit/kits.json` (head, chest, legs, feet, mainhand, offhand); defaults `knight`, `archer`, `none` are written on first run.
- Phase 0: empty Fabric mod scaffold for Minecraft 1.21.11 that loads on server and client.
