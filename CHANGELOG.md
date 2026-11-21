# Changelog

## [Unreleased]

### Mobs and NPCs
- Hostile mobs (zombies, skeletons, creepers, spiders, slimes; not endermen) now notice and attack NPCs, so a creeper next to an NPC swells and explodes. They used to ignore NPCs because vanilla targeting only looks for players. Done with an extra target goal added when a mob loads, plus an access widener for `Mob.targetSelector`.
- NPCs with AI on fight back against hostile mobs, but players and enemy NPCs always take priority: a mob is only chosen when no enemy is in range and is dropped as soon as one appears. Options `hostileMobsAttackNpcs` and `npcsFightHostileMobs` (both on by default) in `combat.json`.

### Phase 5: menu UI, procedural names and skins
- In-game menu (key K, or `/scriptkit menu`) with Difficulty, Kits and Keybinds tabs. Difficulty: sliders for every preset value, save / save as new / reset / delete, default preset, apply to an army. Kits: six slots with an item picker (search and icons), enchantment editor (left-click +, right-click -), save / save as / reset / delete, give to an army. Keybinds: edit combos with click-to-capture, team slots, save and reload, link to vanilla Controls. Layout adapts down to a 427x240 GUI.
- Server-checked menu packets (snapshot request, edit, state, open): gamemaster permission, validation of every item, enchantment, level and name, rate limiting, results shown in the menu. Non-operators see the server tabs read-only.
- Kits can now be saved, overridden and deleted at runtime; `kits.json` stores only custom kits and overrides of built-ins, so built-in updates still apply.
- Players can give themselves a kit: `/scriptkit kit <kit> [players]` and a "me" choice in the Kits tab's Give to selector. Items go in the inventory like `/give` (overflow drops at your feet, worn armor is not replaced); needs operator permission.
- The Kits tab can give a kit to one NPC by name (the Give to selector has a "one NPC by name" choice with a name box), as well as to an army or everyone.
- `/army kit <team|all> <kit>` and `/scriptkit menu`.
- Procedural army names (`PingAbuse`, `BanThis`, `SwordPro`, `xXDarkLordXx`, ...), unique and 3-16 characters; `nameStyle: numbered` restores `red-1`.
- Procedural skins: army NPCs without a chosen skin get a random built-in skin per NPC (no account lookups); an explicit skin still wins, and `skinPool` lets armies draw from a list of usernames.
- UI smoke test (`-Dscriptkit.debug.ui=true`) that opens every tab and saves screenshots; unit tests for names and key names.

### Difficulty
- Per-NPC difficulty presets (`easy`, `normal`, `hard`, `nightmare`), saved in NBT so each army can have its own: damage, health, melee and archer accuracy, jump-crit chance, swing rate, speed, shield skill, reaction speed and detection range. Edit or add presets in `combat.json`.
- `/army spawn ... [difficulty]`, `/army difficulty [<team|all> <level>]`, `/npc difficulty <name|all> <level>`; `/army list` and `/npc info` show the difficulty. Changing it mid-fight takes effect immediately and keeps each NPC's health as a fraction of its new maximum.
- The top-level `meleeAccuracy`, `archerAccuracy` and `critChance` options from the previous change moved into the presets.

### Combat tweaks
- Melee accuracy: swings now connect with a probability set by the difficulty (normal 75%, 15% lower against a moving target), so mismatched armies no longer wipe each other out instantly. Misses swing, play a sound and use up the cooldown. In a dev test 71% of swings connected.
- `/npc info <name>` explains why an NPC is or isn't fighting: its AI state, team, target, and a verdict for each nearby entity.
- `targetCreativePlayers` option (default false) to make NPCs chase and swing at players in creative mode.

### Phase 4: keybinds and networking
- Client keybinds in a "ScriptKit" Controls category: "Toggle all armies" (G) and three unbound "Toggle team" slots whose teams come from `keybinds.json`.
- Key combos from `config/scriptkit/keybinds.json` (sample written on first run): multi-key, exact matching (Ctrl+R does not fire plain R, and plain G is not fired while a modifier is held) and edge-triggered. Actions: toggleAll, toggleTeam, enableAll, disableAll, enableTeam, disableTeam. Bad entries log a clear warning and are skipped.
- Client-to-server `ArmyActionPayload`. The server checks gamemaster permission, validates action and team, rate-limits to 4 packets per second per player, and calls `ArmyManager`. Result shown on the action bar from the server ("Red army: ON (30 NPCs)"), or a reason it didn't happen.
- `ArmyManager.setAi(server, team, state)` is now the single method behind `/army toggle`, `/army enable|disable`-style actions and keybind packets. Toggling a group is now group-wide: if any NPC has AI on they all go off, otherwise all go on (previously each NPC flipped on its own, which scrambled mixed groups).
- `/scriptkit reload` (op): reloads config, combat and kits, and tells connected ScriptKit clients to reload `keybinds.json`.
- One-time chat hint about the keybind after the first `/army toggle` in a session.
- Unit tests (`./gradlew test`) for combo matching and keybind config parsing; dev-only `/scriptkit simulate` with `-Dscriptkit.debug=true`.
- Server works fully without ScriptKit on the client; the client tells you if the server lacks ScriptKit.

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
