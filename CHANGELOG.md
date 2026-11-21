# Changelog

## [Unreleased]

### Phase 3: combat AI
- Replaced the placeholder wander goal with combat AI in the `ai` package: `NpcTargetGoal` (nearest enemy on another team, staggered re-evaluation), `NpcCombatGoal` (melee chase, weapon attack-speed cooldown, strafing, shield blocking with counter-attack timing, stuck handling; bow kiting with full draw, lead and infinite non-pickup arrows) and idle look-around with no wandering.
- `TargetCache`: one shared per-world list of combat-relevant entities, rebuilt at most every 10 ticks, so NPCs never scan the world individually.
- `config/scriptkit/combat.json`: detection range, retarget interval, strafe, block, archer accuracy, damage multiplier, NPCs-can-hurt-players, drop kit on death, death/leave messages.
- Player-style death messages and yellow "left the game" messages when NPCs die (optional on removal too).
- NPC attributes: attack speed 4.0 (weapons modify it like for players), knockback, follow range 48.
- Client renderer shows bow draw and raised-shield arm poses.
- NPC counts exclude dying NPCs, so `/army list` drops an NPC the moment it dies.

### Added
- Kit items can carry enchantments (`{"id": ..., "enchantments": {...}}`), and a built-in `god` kit with maxed-out enchanted netherite armor, sword and shield. Built-in kits are always available; `kits.json` can override them or add more.
- Jump critical hits: melee NPCs sometimes hop before a swing and strike while falling for 1.5x damage with crit particles and sound (`critChance`, default 0.35).
- Players can hurt NPCs on their own team (`playerFriendlyFire`, default true). Same-team NPC-vs-NPC damage is still blocked by default.
- Stuck NPCs mine and build toward their target like a player: pillar up by jumping and placing a block, tunnel through blocks in the way, staircase down. They show the right tool or the block in hand while working, and blocks break in vanilla time. Protected: blocks with storage/data, unbreakable or too-hard blocks (`maxBreakHardness`, default 5) and blocks next to fluids. On by default.
- `/army blocks [on|off]` to toggle mining and building in-game, and a click-to-toggle chat hint after `/army spawn`.
- `/army cleanup` restores every block NPCs mined or placed (logged per world in `data/scriptkit_block_changes.dat`, so it survives restarts). Blocks changed since by someone else are skipped.
- `combat.json` options: `allowBlockBreaking`, `allowBlockPlacing`, `maxBreakHardness`, `pillarBlock`, `dropMinedBlocks`.

### Fixed
- NPC sword swings didn't animate: vanilla only advances the swing timer for monsters and players, so NPCs now do it themselves.
- NPCs now use the real vanilla sprint state when chasing from more than 8 blocks away and when fleeing: sprint particles, sprinting animation, and sprint hits with extra knockback that end the sprint, like a player's. Sprint speed stays at a player's 5.6 blocks/s. They stop sprinting in melee range, while aiming a bow, and when AI is turned off.
- Archers ran at full speed while drawing and could shoot backwards while running away. Drawing now slows them to a player's item-use speed (20%), running cancels the draw (a mostly drawn bow is released as a weaker early shot first), and arrows are only fired when the head and body face the target. Melee NPCs only swing at targets they face.
- NPCs ran far faster than players in combat (sprint ~7.9 blocks/s, strafing up to ~11). In 1.21.11 a mob's ground speed grows with the square of its speed setting, which the earlier tuning missed. Walking is now a player's 4.3 blocks/s, sprinting a player's 5.6, and strafing is set as fractions of walking speed. Saved NPCs get the corrected base speed when loaded, so no respawn is needed.
- Turning AI off and back on left NPCs unable to start new goals (1.21.11 `removeAllGoals` does not stop running goals, so their control flags stayed locked). Running goals are now stopped first.
- Same-team NPC damage is blocked whenever the config's `friendlyFire` is false, even on teams whose own friendly-fire flag is on (for example teams made before the config existed).

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
