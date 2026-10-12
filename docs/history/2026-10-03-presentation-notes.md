> Historical record. Original: `PRESENTATION_NOTES.txt`. Current reference: [04-PROCESSING.md](../04-PROCESSING.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

```text
CURRENT BEHAVIOR CORRECTION - October 4, 2026
Harvest now commits to a persistent controller kill-output buffer regardless of
external staging capacity. All further kills pause until that shared list is
empty; generic unreserved OUTPUT ports drain it vat-wide, including bottom ports.
Earlier external-capacity/adjacent-output harvest descriptions and manual checks
below are historical. Routed item metrics now count actual staging transfers.
See 2026-10-04-kill-output-notes.md for current behavior, compatibility and 39-test results.

MobVats Slice 3 - Controller GUI and presentation
October 3, 2026

1. Controller GUI
   VatControllerEntity implements MenuProvider. Ordinary right-click opens the
   registered mobvats:vat_controller menu (crouch retains ZeroCore validation).
   VatControllerMenu extends ZeroCore ModTileContainer, with an empty slot factory
   and sampled StringData / ItemStackData. No writable process controls, route
   selection, priority selection or staging inventory are added.
   VatControllerScreen has local Overview, Production, Efficiency and Diagnostics
   tabs, wrapped text, scrolling and a tooltip for a representative assigned tool.
   Diagnostics show all tool names, modes and relevant staging information, with
   a bounded approximately 128-line summary. Existing port mode menus are unchanged.

2. Metrics model / window
   Controller-owned MachineMetrics uses named extensible counters, each with a
   200-active-tick ring (10 seconds at 20 TPS). During startup the divisor is the
   available active sample duration. Unformed machines pause the window.
   Lifetime totals persist in controller FullSync NBT under metrics. Recent
   samples reset on reload, avoiding a fabricated offline rate. Assimilation
   merges lifetime totals; source recent samples are not imported.

3. Throughput
   Milk mB/s measures successful passive milk production, not fluid insertion or
   extraction. Routed items/s counts successful output-staging commits, including
   milk buckets and harvested items, not hopper withdrawal. XP/s counts generated
   harvest XP. Blocked retries, simulated transfers and failed output plans do not
   count. Lifetime wheat, harvest-item, harvest-count and durability counters are
   recorded at the existing process commit boundaries.

4. Efficiency
   Lifetime harvest items / wheat consumed, XP / wheat consumed, and actual tool
   durability consumed / committed harvest. Zero-denominator ratios display N/A.
   Milk input cost is explicitly none. Wheat for a replacement still growing is
   already in the denominator, so these ratios can change as it becomes READY
   and is harvested. No aggregate efficiency percentage is invented.

5. Diagnostics
   Formed/active status, simulacra count, fixed 10-tick cadence, milk and actual
   tank contents/capacity, encoded specimen identity and coordinates, replacement
   state/progress/remaining ticks/readiness, passive milk timer, XP, port counts,
   assigned/unassigned tool counts, compiled feed sources and actual wheat stock.
   Tool diagnostics include selected head, held item, missing valid tool, waiting
   for milk/process readiness, ready harvest, blocked output/resident bucket, and
   unsupported held-item process. Rejected alternatives are unassigned.
   Item staging, Item/Fluid I/O modes, compiled feed/tool endpoints and delegated
   tank availability are observations, not configuration controls.

6. Held-item rendering
   VatMechanismRenderer reads each Tool Port's real synced heldItem and calls the
   vanilla ItemRenderer with ItemDisplayContext.FIXED. Actual stack components,
   models, model overrides and enchantment glint are retained. No replacement
   tool art, item-type-specific branch or additional inventory is introduced.

7. Tool arm
   Controller compiles inner-shell bases and cardinal targets into VatPresentation.
   Bases meet the existing three-pixel wall shell. Selected tool targets intersect
   specimen body bounds, ending 0.12 blocks inside their compiled inward ray.
   The renderer does not discover routes or target the animated head.
   Cached sleeve geometry and reusable middle/tip meshes form a simple straight
   telescoping arm. Idle length is up to 0.28 blocks. Actual item is at its tip.
   A successful process commit publishes a world-time interaction start on the
   Tool Port. FULL interpolates 6 ticks extend, 4 ticks hold, 10 ticks retract.
   This is a cosmetic post-commit pulse: production remains instantaneous with
   its existing cadence. No delayed commit, per-part tick or gameplay animation
   timer is introduced. If a bucket routes immediately or a sword breaks, its
   actual held slot is empty and the mechanism moves without a duplicate item.

8. Feeding tube
   Only compiled Item Input -> specimen contacts create hoses. Endpoints use the
   generic specimen body anchor. A cached 0.09-block square tube with slight sag
   and 12 segments provides the initial hose representation. There is no wheat
   item, feed arm or particle animation. Mode/assignment changes rebuild anchors;
   machine pause/disassembly/part removal clears them on the controller.

9. Client sync
   Server controller creates immutable tool/feed anchors when topology rebuilds.
   Client controller assembly maintains bounds only; it does not compile routes.
   Equality suppresses unchanged anchor updates (e.g. sword durability changes).
   Head is only the transport/cache carrier; it performs no geometry calculations.
   Static anchors are in Head network tags for fresh chunk viewers and change
   notifications, separate from routine controller progress updates. Client-local
   revalidation does not clear server-authored anchors during chunk arrival.
   Controller NetworkUpdate now carries tank + XP + compact per-head cycle/timers/
   pending count. Server process state, pending loot, pending weapon and metrics
   history remain FullSync/persistence only; no route graph is serialized there.
   GUI uses ZeroCore changed-value sampling every 10 broadcasts, including rates,
   ratios and diagnostics. Optional whole-part menu NBT synchronization is disabled.
   Screen opening acknowledges ZeroCore's data channel. Tool ItemStack sync remains
   its existing real held-slot NBT channel, with an ephemeral interaction timestamp.

10. Render tiers
   One VatRenderDetail decision still uses nearest-point distance to the whole vat
   AABB. Configured BER view distance, whole multiblock bounds and offscreen behavior
   remain intact. FULL: specimen/head tracking/straps, actual tools, animated arms,
   static hoses. REDUCED: static specimen/head, straps, actual tools, retracted static
   arms, static hoses. MINIMAL: static specimen only. CULLED: return before specimen
   or any cable/mechanism work. No independent feature distance checks were added.

11. Verification
   Run .\gradlew.bat --offline build and runGameTestServer with the existing cache.
   Five new autonomous headless GameTests cover rolling expiration/ratios/persistence,
   production counters/arm signal, assignment/feed geometry changes, compact status/
   held components, and registered menu provider/validity/ZeroCore codec delivery.
   Existing blocked-bucket test also checks that retries do not inflate item totals.
   Minecraft's mock network connection lacks NeoForge payload negotiation, so the
   menu test exercises the provider and actual data codecs without opening a client.
   Final result: build/resource verification and all 33 required GameTests passed.
   Actual GUI opening/layout and rendering remain manual acceptance.
   No runClient, runClientSmokeTest, runClientReloadTest, mouse/keyboard or screenshot
   testing was performed. Resource checks preserve the seven documented PNG hashes.

12. Manual acceptance (user only)
   Ordinary controller click: open all four tabs, scroll diagnostics, verify milk,
   growth/READY state, XP, rates, lifetime ratios, feed stock and blocked reasons.
   Fill outputs, remove a sword, empty wheat, insert a non-milk fluid and inspect
   the corresponding explanations. Crouch-click should still show validation.
   Mount sword/bucket/custom tool: check actual model, glint, idle placement and
   target direction. Trigger a harvest/milk commit: check extension and retraction;
   immediate routed/broken items should disappear rather than be duplicated.
   Valid feed inputs show hoses; unrelated inputs do not. Change input mode or
   break/reform vat and verify hose/arm geometry clears/rebuilds.
   Verify FULL, REDUCED, MINIMAL and CULLED at configured nearest-surface thresholds;
   return inward, reload chunks/rejoin and confirm cached presentation restores.

13. Performance / cleanup
   No new per-tick topology discovery or production/drop calculations in rendering.
   Metrics touch a small fixed ring per registered key. GUI summaries are created
   on demand, shared per controller/10-tick interval across viewers. Static anchors,
   hoses, sleeves and rods are cached; frame work is arm transforms and vanilla
   item dispatch. Many loaded vats with many mounted custom items can still make
   FULL/REDUCED item rendering costly; profile after user acceptance before adding
   further complexity. GUI text is initially English; localization and dedicated
   art/theme are follow-up polish, not production mechanics.

14. Recommended Slice 4
   After manual acceptance, define typed product/process diagnostic identifiers
   and localize the observer UI, then choose one explicitly scoped next production
   slice with provisional balance tests. Keep physical topology as configuration.
   This slice does not implement the listed Blender/MEAT/waste/XP-output/upgrades/
   additional-species or multiple-simulacrum scheduling features.

Sources read
   Requested ExtremeReactors2 controller part/screen patterns, downloaded under
   ../examples/ExtremeReactors2-gui (reference only, not shipped):
   https://github.com/ZeroNoRyouki/ExtremeReactors2/blob/master/src/main/java/it/zerono/mods/extremereactors/gamecontent/multiblock/reactor/part/ReactorControllerEntity.java
   https://github.com/ZeroNoRyouki/ExtremeReactors2/blob/master/src/main/java/it/zerono/mods/extremereactors/gamecontent/multiblock/reactor/client/screen/ReactorControllerScreen.java
   Installed ZeroCore2 1.21.1-2.4.18 ModTileContainer and sampled data/codec APIs
   inspected directly; current Minecraft/NeoForge sources checked for ItemRenderer.
   Adapted to NeoForge 21.1.252 rather than copying old Forge signatures or art.

No publication, release or client launch. Slice 3 changes remain local.

Mechanism render corrections - October 4, 2026
  Controller-compiled feeding endpoints now target the neutral adult cow head-box
  center (model pivot plus box center), rotated to the placed specimen facing.
  The endpoint stays cached; head tracking does not rebake the hose each frame.
  Tool item planes run along the arm travel axis; vanilla generated swords have
  their handle-to-tip diagonal aligned with it. Models use their resolved
  third-person right-hand scale with no extra half-scale, and NONE display context
  avoids a second display rotation. Vanilla sword size is 0.85 instead of 0.5
  blocks per model unit (1.7 times the prior size). Custom models retain their own
  baked held scale; their semantic blade/grip axes cannot be inferred generally.
  Build/resource validation and all 39 headless GameTests pass. The controller
  presentation test checks the head endpoint against vanilla CowModel geometry.
  No client task ran; visual acceptance remains with the user.

Tool mount correction - October 4, 2026
  Supersedes the diagonal-to-axis rotation above: native image right now faces
  the specimen, keeping swords naturally diagonal. MobVats syringe is reversed
  so its left-side needle faces the specimen. The centered model is offset by
  half its held X scale to mount the rear image edge at the arm tip and prevent
  the standard item footprint from extending through the back of the panel.
  Held size is unchanged; no client task ran. User visual acceptance remains.

Sword grip attachment correction - October 4, 2026
  Sword-tagged items now mount the native grip near image pixel (3,3), measured
  from bottom left, at the arm tip. Both vertical and travel offsets compensate
  for the grip position at held scale, replacing the previous mid-height image
  boundary anchor that visibly detached the sword. Natural diagonal/right-edge
  orientation and the syringe reversal remain unchanged. Custom sword artwork
  may eventually need explicit grip metadata rather than the vanilla fallback.
```
