> Historical record. Original: `KILL_OUTPUT_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
SLICE 4 CORRECTION - October 4, 2026
The item backlog now gates only item-producing Sword harvest checks. Fluid-only
Blender harvest can consume the shared READY replacement while committed Sword
items remain buffered. See 2026-10-04-slice4-notes.md for selected actor priority, product
buffers and the complete 47-test results. Earlier global-kill wording below is
historical; item buffer persistence/draining/reservations remain applicable.

Kill output buffer / generic Item Outputs - October 4, 2026
==========================================================

1. Controller representation and persistence
   MultiblockVat owns one KillOutputBuffer component with a private List<ItemStack>.
   Committed drops are copied into it; snapshots are defensive copies. There is
   no fixed staging-slot limit on this list. Item Ports retain exactly ONE slot.
   Controller persistent/FullSync NBT stores "killOutputs" using registry-aware
   ItemStack serialization, including item components. Missing data loads empty.
   NetworkUpdate sends only killOutputStacks / killOutputItems scalar counts.
   Pause/disassembly preserves committed drops; processing waits for assembly.
   Assimilation appends both controllers' committed stacks and clears the consumed
   controller's buffer so reconnection does not discard a backlog.

2. Exact global harvest backpressure
   Every specimen shares this one backlog. While it is nonempty, the scheduler
   skips new harvest eligibility/loot work and automatic sword acquisition.
   Each harvest entry point also checks it before preparing a kill. Milk,
   bucket routing, feeding and replacement growth remain independent.
   The list must become entirely empty before another kill can commit. Items
   already moved into external staging no longer belong to the kill buffer.

3. Generic versus reserved outputs
   Generic = all assembled OUTPUT Item Ports minus valid physical actor-local
   reservations. Ports anywhere on the shell, including the bottom, can qualify.
   Adjacency to a sword actor is unnecessary. Inputs cannot drain the backlog.
   Valid compatible/reaching actor candidates reserve adjacent OUTPUT endpoints,
   including alternatives that lose an exclusive active claim. Valid empty Tool
   loading connections also reserve their adjacent endpoints. This preserves
   local workflows while waiting/loading; rejected actors remain non-executable.

4. Topology compilation
   VatTopology adds reservedItemOutputs and genericItemOutputs. The compiler
   derives both from existing physical candidates/loading relationships and its
   sorted OUTPUT set. Feed contacts, tool targeting, actor priorities and physical
   active claim selection remain unchanged. No client topology compiler is added.
   Current validation still requires the existing single Head/Harness/Drain/vat
   Controller; this fix does not enable multiple specimen structures.

5. Harvest commit
   A READY specimen plus selected valid sword actor rolls/reuses prepared loot.
   Weapon damage is prepared on a copy; assembly, selected route, READY state,
   Tool ownership and held weapon are rechecked before buffer insertion.
   Buffer insertion prepares copies atomically and rejects invalid drops or an
   occupied backlog. On success the controller adds XP, records harvest/tool
   metrics, updates the Tool, consumes READY into IDLE and clears pending loot.
   External output capacity is no longer a kill preflight requirement. Empty loot
   still commits correctly. No items or XP orbs are spawned in the world.
   Produced-item efficiency counts at kill commit; routed items/s counts actual
   transfers into staging, avoiding counting buffered drops as already routed.

6. Drain order and cadence
   On the normal 10-tick controller process cadence, drain before harvest work.
   Try stored stacks in list order and generic outputs in X, then Y, then Z order.
   Insert directly into each owned one-slot OUTPUT staging inventory, retaining
   actual remainders. Partial stack and partial list draining are supported.
   A single bottom output can stage beef, then leather after hopper extraction.
   Once the whole backlog empties a READY kill may run in the same cadence; newly
   committed drops drain on a subsequent cadence. No generic ports means safe
   storage with global kill pause, not deletion or world item spawning.

7. Status and diagnostics
   Overview shows generic/reserved output counts. Production shows buffered
   stacks/items; the controller summary also shows the buffered stack count.
   Diagnostics show "Harvest output buffered: N stacks", global kill pause, and
   generic drain/wait state or safe storage when no generic outputs exist.
   Port diagnostics identify reserved actor-local versus generic kill output.
   Harvest readiness no longer reports external output blockage. Existing milk
   blockage/resident bucket messages still apply to the local bucket workflow.

8. Verification
   Ran gradlew.bat --offline build runGameTestServer using the existing cache.
   Build, packaged resources/art hashes and all 39 required headless GameTests
   passed. No conventional unit-test sources are present; coverage is GameTests.
   Six new tests cover a real bottom output/hopper, deterministic port ordering,
   actor reservations (including empty Tool), partial stack conservation, rejected
   buffer deposits/assimilation persistence, global second-specimen backpressure,
   and automatic sword loading/commit with no Item Outputs at all.
   Updated existing tests cover XP/durability/READY consumption, two-stack commit
   with full external staging, persistence including item components, safe storage
   without generic ports, partial drain/resumption, independent milk/feeding,
   sword reuse/breakage/enchantments, scalar status sync and throughput metrics.
   The second-specimen test is a synthetic controller-component fixture using two
   assemblies and compiled routes; it does not weaken production structure rules
   or claim that a two-Head world vat is supported. The hopper test uses a real
   headless world HopperBlockEntity and verifies exact item totals/no spawned loot.

9. Compatibility and future filters
   Existing saves without killOutputs load an empty buffer. Existing prepared
   pending loot remains reusable when its recorded weapon matches the mounted
   weapon; it now commits into the shared buffer rather than waiting for local
   external capacity. Previously staged items stay in their port inventories.
   Automation expecting sword-adjacent outputs must add a generic unreserved
   OUTPUT port; those adjacent ports remain reserved for local workflows.
   Future filters can constrain generic endpoint selection without changing the
   controller backlog/global pause rule; nonmatching drops must remain stored.
   No filters, predicates, matching priorities, filter UI or slot expansion added.

Files
   gamecontent/multiblock/vat/MultiblockVat.java
   gamecontent/multiblock/vat/VatTopology.java / VatTopologyCompiler.java
   gamecontent/multiblock/vat/VatStatusSnapshot.java
   gamecontent/multiblock/vat/process/KillOutputBuffer.java (new)
   gamecontent/multiblock/vat/process/CowProcesses.java / CowProcessState.java
   gametest/CowProcessGameTests.java

Manual verification remains with the user: form a fed sword vat with a generic
bottom OUTPUT and hopper; confirm buffered drops drain, kills pause under full
staging and resume once the backlog is empty, while milk continues. Save/reload
with a backlog and inspect the controller's Production/Diagnostics tabs.

No client task, publication, release, commit or push performed for this fix.
```
