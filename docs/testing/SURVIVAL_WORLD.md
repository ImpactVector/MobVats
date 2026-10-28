# Survival acceptance workshop

From the repository (`src/`) with Java 21:

```powershell
.\gradlew buildSurvivalWorld
.\gradlew runSurvival
```

`runSurvival` opens the prepared singleplayer world directly. It preserves progress
and fails if preparation has not completed. All saves, options, and client configs
are confined to `run-survival/`; ordinary `runClient` is unchanged. The headless
preparation server uses `run-survival/generator/`, localhost, and an ephemeral port.
No manual commands, inventory setup, or Creative mode are required.

World: `run-survival/saves/MobVatsSurvival/`. Rebuilding refuses an existing folder,
including a failed/incomplete generation. Close Minecraft before explicitly
discarding that world's progress:

```powershell
.\gradlew buildSurvivalWorld -PresetSurvivalWorld=true
```

Reset only accepts the fixed world path and the generator's exact ownership
marker, refuses links/junctions, checks the Minecraft session lock, and serializes
generation with a separate lock. Back up progress before resetting. Never remove
the ownership marker to work around a refusal.

## Pinned development runtime

Minecraft 1.21.1, NeoForge 21.1.252, current MobVats sources, and:

| Mod | Version | Pinned distribution ID |
| --- | --- | --- |
| Functional Storage | 1.21.1-1.5.8 | CurseForge file 8459097 |
| Titanium (required by Functional Storage) | 1.21-4.0.51 | vbCgpf68 |
| Tom's Simple Storage | 1.21-2.4.2 NeoForge | WQAMoIMC |
| JEI | 19.57.0.451 NeoForge | RI8WCow6 |
| ZeroCore (required by MobVats) | 1.21.1-2.4.18 | CurseForge file 6648746 |

Dependencies resolve through official Modrinth Maven and CurseMaven. JEI supplies
its bundled MezzConfig dependency. Version pins are in `gradle/survival.gradle`;
the source sets and mod mappings keep these additions out of normal runs and the
published MobVats JAR. `src/survival/` is a separate development fixture mod.

Official release metadata:
[Functional Storage](https://www.curseforge.com/minecraft/mc-mods/functional-storage/files/8459097),
[Titanium](https://modrinth.com/mod/titanium/version/vbCgpf68),
[Tom's Storage](https://modrinth.com/mod/toms-storage/version/WQAMoIMC),
[JEI](https://modrinth.com/mod/jei/version/RI8WCow6).

Functional Storage 1.5.7 was rejected by the reload smoke test: its synchronous
controller lookup during drawer chunk loading deadlocked. The 1.5.8 pin contains
the upstream loaded-chunk lookup fix.

## Layout and supplies

The stone-brick workshop has a 41 × 41 clear interior footprint, 14 blocks of
headroom (the T3 limit is 12), a solid roof, permanent floor/ceiling lighting, and bottom slabs over
the roof. The central construction area is empty. It is enclosed without doors;
break and replace a wall section if access outside is needed. The surrounding
world is seeded superflat with structures disabled. Survival, Normal difficulty,
cheats disabled, daylight, mob spawning, hunger, and ordinary consumption remain
active. The world spawn is `(5, -60, 7)`; spawn radius is zero.

Near spawn: three of each diamond tool, food, torches, ladders, buckets, drawer
tools, crafting table, furnace, and a red bed. The north wall carries upgraded,
locked Functional Storage drawers and an empty linked compacting drawer. The
controller at `(21, -60, 3)` connects through Tom's inventory connector to the
crafting terminal at `(21, -60, 5)`. An air gap separates the controller from the
drawer bank to prevent counting both aggregate and member inventories. Use the terminal to retrieve ingredients and
craft directly from the drawers. Locked drawers still allow normal extraction.

Stock includes all registered non-Creative MobVats items, including all three
tiers, shared machine parts, conduits, buffers, products, and available fluid
buckets. Stackable items receive 4,096 each; unstackable items receive 64 each.
Vanilla MobVats recipes and Mixer item selectors add their ingredients. The
recipe graph is then recursively expanded, retaining intermediates as separate
stock entries. Tag ingredients use one deterministic valid representative,
preferring oak wood. All MobVats recipes are scanned; each intermediate uses one
deterministic recipe, preferring crafting over processing. The exact generated
inventory and drawer coordinates are recorded in `survival-stock.csv` in the save.

The south wall supplies four full MobVats fluid buffers (16 buckets) per registered
MobVats source fluid, including Strange Meat. Use the stock Strange Meat buckets
or connect fluid logistics to the buffers to test Blood Iron without mob farming.
Each buffer is finite, initialized via its real tank API. A 2 × 2 renewable water
pool is near `(37, -61, 39)`. Fluids without bucket items are provided as buffers.

## Changing stock

Edit `SurvivalStock.java` in `src/survival/java/net/impactvector/mobvats/survival/`:
`EXTRAS` adds vanilla ingredients/equipment; the two count constants control stock
amounts. New MobVats registrations and recipes are discovered on the next rebuild.
Creative devices are excluded by their `creative_` registry prefix. Missing item
IDs, unresolved ingredients, capacity overflow, and failed drawer links fail
generation. The current bank allows 150 stocked drawers; expand its layout and
verification together if the recipe graph exceeds that limit. Custom operator
recipe changes are not copied from ordinary development configurations.

## Automated and manual acceptance

Generation waits for ordinary network discovery, verifies every stocked drawer
through Tom's terminal, crafts four copper Vat casings through its real result
slot with an empty Survival FakePlayer inventory, checks drawer consumption, and
restores the two test ingredients. It also checks fluid supplies, walls, roof,
lighting above every solid interior surface, tools, bed, unobstructed construction
space, vanilla first-login spawn selection, and saved game mode/difficulty/cheats settings. A success
receipt is written only after Minecraft saves and shuts down.

Reload the freshly prepared save in another headless server to check persistence:

```powershell
.\gradlew runSurvivalWorldSmokeServer
```

This repeats content and crafting assertions and writes `survival-smoke-complete.txt`.
Use it before playing: it expects initial stock counts, advances ordinary game
ticks, and saves. It will fail after resources are consumed during acceptance play.
Logs are under `run-survival/generator/logs/`.

Run normal project validation with `spotlessCheck build runGameTestServer`.
The generator is compiled by `check`; it is not automatically executed or reset by
builds or normal GameTests. No automated client is launched. Manual client
acceptance still covers first-entry presentation, drawer labels/fronts, bed use,
JEI recipe transfer, and comfortable construction of a maximum-sized T3 Vat.

Verified on October 28, 2026: generation and headless reload passed with 108 stocked
drawers and 28 full fluid buffers. Repeated generation produced identical stock
manifests. The runtime isolation check verifies that the added dependencies and
fixture mod are absent from ordinary runs and the packaged MobVats JAR.

Mob tier defaults were reviewed after initial validation exposed an incorrect
T3 Skeleton assignment. The reviewed assignments are:

| Tier | Mobs |
| --- | --- |
| T1 | Villager |
| T2 | Iron Golem, Skeleton, Snow Golem |
| T3 | Evoker, Breeze, Guardian, Illusioner, Phantom |

These are explicit initial species assignments; biome/category inference cannot
promote Skeletons or Golems above T2. Existing authored profiles remain
authoritative. The generated GameTest and Survival-generator profiles in this
checkout were updated to match without rebuilding either world.

After the tier review, `spotlessCheck build runGameTestServer` passed, including
all 1,182 required GameTests and the three formerly failing Skeleton fixtures.
