> Historical record. Original: `COW_PROCESS_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
CURRENT BEHAVIOR CORRECTION - October 4, 2026
Harvest now commits to a persistent controller kill-output buffer regardless of
external staging capacity. All further kills pause until that shared list is
empty; generic unreserved OUTPUT ports drain it vat-wide, including bottom ports.
Earlier external-capacity/adjacent-output harvest descriptions and manual checks
below are historical. Routed item metrics now count actual staging transfers.
See 2026-10-04-kill-output-notes.md for current behavior, compatibility and 39-test results.

MobVats Slice 2 -- Cow Processes + Item Routing -- October 3, 2026

Current repository: C:\Projects\MobVats\src
Minecraft 1.21.1 / NeoForge 21.1.252 / ZeroCore2 1.21.1-2.4.18
Prior uncommitted Slice 1 and client-culling work was retained. No client run,
commit, push, publication or release was performed for this slice.

Implementation files under src/main/java/net/impactvector/mobvats/
  gamecontent/multiblock/vat/process/CowBalance.java
  gamecontent/multiblock/vat/process/CowProcesses.java
  gamecontent/multiblock/vat/process/CowProcessState.java
  gamecontent/multiblock/vat/process/CowHarvestLoot.java
  gamecontent/multiblock/vat/process/VatItemTransaction.java
  MultiblockVat owns CowProcesses and FluidTank. VatActor/VatTopology/compiler
  extend the active topology; Tool Port exposes state mutation but never ticks
  processes. MobVats enables NeoForge milk. VatPartBlock shows controller status.
  Head retains inactive legacy NBT but no production/output mutation methods.
  CowProcessGameTests adds ten autonomous headless tests.

1. Milk state / storage
  Controller's shared 4000 mB FluidTank stores minecraft:milk, enabled with
  NeoForgeMod.enableMilkFluid() in the mod constructor and NeoForgeMod.MILK.
  Every validated Head has controller-owned milkTicks. At 1200 active ticks,
  cadence attempts one whole 1000 mB insertion and resets only on success. Full
  or incompatible tanks retain a completed interval until space is available.
  No wheat, tool, power or durability is required. No offline catch-up occurs.
  Input remains generic; this provisional single-fluid tank cannot mix milk with
  other supplied fluids. Drain non-milk contents before milk can accumulate.

2. Milk bucket process
  A selected physical milk-use Tool with a bucket and >=1000 mB converts to one
  vanilla Milk Bucket. Exactly 1000 mB is consumed once. The one-item held slot
  safely retains the result if adjacent Outputs cannot accept it. Later cadence
  retries routing that resident result before another bucket can be acquired.
  No dropping, deletion, repeated consumption or replacement dependency.
  A minimum 3x4x3 manual bucket vat requires no automated Input/Output cell.

3. Automated Input -> Tool -> Output
  Empty tools compile stable geometric loading connections separately from
  extraction claims. Available unclaimed processes acquire one appropriate item
  from adjacent Input staging. READY harvest prefers a sword with an adjacent
  output endpoint; otherwise available milk can acquire a bucket. Sockets,
  sources and outputs use deterministic coordinate ordering. Irrelevant items
  remain staged. Reusable swords stay mounted; bucket results route or remain.
  Actual held changes recompile claims. Ordinary staging contents do not require
  geometric rediscovery. No Tool pulls/pushes or independently schedules work.

4. Feeding relationship
  Uses compiled direct cardinal Item Input -> Head contacts. An inward ray from
  the wall must hit the body's registry AABB without intervening collision or a
  nearer specimen. Own Head/Flesh substrate is ignored. Noncontact Inputs and
  Tool-held wheat cannot feed. No Feed Port or feeding tube rendering was added.

5. Replacement cycle
  Per-Head controller state: IDLE -> consume one wheat -> GROWING -> after 1200
  active ticks READY. READY represents exactly one harvestable unit and blocks
  further feed. A successful selected sword harvest consumes it and returns
  IDLE. Feeding/slaughter form one replacement loop, not separate schedulers.
  Milk remains independent. No additional Cow or breeding entity is created.

6. Vanilla loot / XP APIs
  CowHarvestLoot uses fresh transient EntityType.COW.create(ServerLevel) and
  NeoForge FakePlayer context equipped with the sword copy. Neither is added to
  the Level/entity manager, ticked, killed or persisted. Cow AI is disabled.
  No die/dropAllDeathLoot/spawnAtLocation path is invoked.
  cow.getLootTable() resolves through reloadableRegistries().getLootTable().
  LootParams ENTITY includes THIS_ENTITY, ORIGIN, DAMAGE_SOURCE, ATTACKING_ENTITY,
  DIRECT_ATTACKING_ENTITY and LAST_DAMAGE_PLAYER, with playerAttack source.
  LootTable.getRandomItems(params,seed) uses current datapack tables and NeoForge
  global loot modifiers; the attacker equipment supplies Looting context.
  Cow.getExperienceReward(level,attacker) supplies vanilla 1..3 XP plus enchantment
  effects; EventHooks.getExperienceDrop applies NeoForge's XP hook. A nonnegative
  saturating controller long buffers XP without orb entities. doMobLoot=false
  yields no items/XP. This simulates loot tables, not full LivingDrops/death events.

7. Tool / enchantment semantics
  Eligibility: ItemTags.SWORDS or NeoForge SWORD_SWEEP ability, no material list.
  Generic Tool Port has no Sword/Bucket subclass. Fire Aspect gameplay enchantment
  level marks the transient victim burning, allowing the vanilla furnace-smelt
  loot predicate to produce cooked beef. No on-hit world effects run in preview.
  Successful harvest uses item hurtEnemy/postHurtEnemy hooks. Vanilla swords call
  ItemStack.hurtAndBreak(1,...), respecting NeoForge damageItem, Unbreaking and
  unbreakable components. Breakage clears the mounted slot. Blocked retries apply
  no durability. Mod combat item hooks are reused; arbitrary mod death behavior
  and enchantment effects beyond this loot context are not guaranteed.

8. Atomic output handling
  READY rolls once for the mounted weapon and caches drops, XP and weapon signature
  in controller state. Retry/reload reuses that result; deliberate weapon mutation
  or replacement changes the context and invalidates the roll.
  VatItemTransaction creates shadow ItemStackHandlers from all compiled Output
  staging snapshots and inserts all results into those same shadows. Different
  drops cannot falsely reserve the same empty slot. Any remainder prevents all
  real output, READY consumption, XP and durability changes.
  Commit rechecks every snapshot and Output mode. It uses only private MobVats
  one-slot handlers on the server thread, so hopper ticks cannot interleave and
  arbitrary external handlers cannot disagree with simulated acceptance.
  Complete insertion is followed by buffered XP, resident sword combat wear,
  READY -> IDLE and cache clearing. Pending data is a narrow preview, not storage
  for completed production or an independently ticking transaction network.

9. Fluid Output
  Slice 1 FluidPortHandlerForge / IFluidPort / Passive delegation wraps controller
  storage with direction policy. Output drains milk through FluidHandler.BLOCK;
  Input is insertion-only generic IO. Broken vats delegate to EmptyFluidHandler.
  No active pump or pipes were added.

10. Drain / legacy Head
  Drain remains the required structural sump beneath the construction column.
  No item/fluid capability or direct bucket-to-milk interaction. Legacy Head
  milkTicks/milkReady stays readable and round-trippable but inactive; it is not
  automatically credited to the new tank. Active Head producer methods are removed.

11. Cadence / persistence / diagnostics
  MultiblockVat.updateServer() invokes its component only while formed. Cheap
  counters advance each active tick; feed, loading, routing and harvest checks run
  every ten ticks. Break/pause clears executable topology and pauses timers.
  Reformation preserves matching per-Head state. Compatible assimilation retains
  records and adds XP, with existing provisional fluid merge limits.
  ZeroCore reference-coordinate syncDataTo/From saves fluidBuffer, cowXp and
  cowProcesses. Head records contain position, cycle, milk/replacement ticks and
  optional pendingItems/pendingXp/pendingWeapon. Held items and staging/modes keep
  part NBT. No live entity references are serialized. Assembly prunes unrelated
  old Heads; only one adult cow is supported by current structural validation.
  getTopology(), cowState(head), milkAmount() and getXp() expose debug state,
  including assigned tools and feed sources in topology. Ordinary controller
  right-click shows milk, replacement progress and XP; sneak-click preserves
  ZeroCore validation. Tool feedback and Item/Fluid menus retain their diagnostics.

12. Verification
  .\gradlew.bat --offline build runGameTestServer
  Build, verifyRenderResources and all 28 required headless GameTests pass.
  Added CowProcessGameTests:
    passiveMilkFluidAndDrain: real 1200 ticks, fluid delegation/policy, no Drain IO.
    minimumManualMilkBucket: real passive timer, manual transform/retrieve, retries.
    automatedMilkBlockedOutputAndRetries: three-bucket conservation, blocking/resume.
    physicalFeedingReplacementReady: real growth, one wheat, missing output safety.
    irrelevantItemsAndNonFeedInputs: physical feed and process-driven acquisition.
    swordHarvestAtomicAndReusable: two-kind vanilla loot, aggregate capacity, retry
      reload, staged sword acquisition, XP/READY/wear and reusable tool cycles.
    swordBreakageCommit: final durability and no duplicate harvest.
    cowProcessPersistenceAndPause: milk/growth/XP/tool NBT, reference-coordinate
      serialization, break/pause/repair/resume.
    emptyConnectionsAndIndependentClaims: mounted vs empty routes, parallel uses,
      stable competing swords, excluded actor cannot run, removal recompile.
    cowLootEnchantmentsAndDurability: 64 seeded vanilla/Looting/Fire Aspect rolls,
      cooked beef, XP range, Unbreaking, unbreakable and ordinary sword wear.
  Production tests assert no Cow, XP orb or dropped ItemEntity in vat bounds.
  Existing 18 tests remain green, including actual hopper IO, mode persistence,
  LOS, priorities, validation, rendering tiers/bounds and model geometry.
  Resource checks verify packaged model/atlas/material references and seven
  preserved PNG hashes. Transaction tests seed NBT; separate tests run actual
  intervals. No subjective/client rendering, disk restart or third-party pipe
  interoperability acceptance was performed. Optional client-harness source now
  reads controller milk progress, but no client task was run.

13. Provisional constants / limits
  CowBalance: cadence 10 ticks; milk 1000 mB / 1200 ticks; tank 4000 mB;
  wheat cost 1; replacement interval 1200 ticks. At 20 TPS intervals are 60 seconds
  and checks occur every 0.5 seconds. No balancing config added.
  One cow, one single-fluid buffer, no XP withdrawal. Compatible assimilation
  accepts up to tank capacity; incompatible stores/overflow retain Slice 1 limits.
  Rejected actors are discarded. Empty physical loading connections are not
  extraction alternatives. Future Blender claims cowHarvest with higher priority.
  No rendered tools, animation, feed tubes, Blender/MEAT/Sewer/Waste, upgrades,
  new biological fluids or multi-specimen scheduling was implemented.

14. User manual checks (not performed by an agent)
  - Mount an empty bucket in a minimum vat; wait ~60 active seconds and retrieve
    the Milk Bucket with empty hand. Tool Port must not open a GUI.
  - Add adjacent Input/Tool/Output, supply buckets with a hopper and extract output
    with a hopper. Fill output and verify safe resident blocking/resume.
  - Connect Fluid Output to a compatible external milk consumer.
  - Supply wheat through a cardinal Item Input aimed at the cow. Mount or stage a
    sword; after ~60 seconds provide room for both beef and leather outputs.
  - Controller right-click: IDLE/GROWING/READY, milk and increasing XP. Sword must
    remain mounted, wear, break safely and respect supported enchantments.
  - Block output: READY, XP and durability stay unchanged until capacity returns.
  - Save/reload and break/reform: retain milk, timers, READY, XP and tools.
  - Drain leaves bucket unchanged. Existing vat visuals/facing/tiers remain intact;
    held tools and feeding tubes are not rendered in this slice.

15. Recommended Slice 3
  Establish held-ItemStack rendering and activation/sync contracts for tool/feed
  connections, with user visual acceptance. Then add one higher-priority extractor
  using the same cowHarvest claim and transactional controller scheduler. Blender/
  MEAT and Sewer/Waste require explicit later scope. Keep XP output, upgrades and
  multi-specimen scheduling separate.
```
