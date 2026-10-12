> Historical record. Original: `docs/MEDIUM_LIQUID_ACCEPTANCE.md`. Current reference: [07-TESTING.md](../07-TESTING.md).
> Archive date basis: Git introduction author date 2026-10-11T15:15:15-05:00; report event date is not independently established. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Vat medium and liquid visibility acceptance

Two tracked workstreams for development acceptance. Public release validation remains separate.

## T-01: Vat transfer, diagnostics, and visible medium

- [x] Trace creative source, active boundary, inward TAP, physical buffer, Fill Valve, and authoritative medium.
- [x] Inspect existing world NBT before rebuilding.
- [x] Replace ambiguous Fill Valve failure with operation-derived diagnostics.
- [x] Add finite routed water and Mixer-produced compound culture regressions.
- [x] Add glass-height clipping and a horizontal medium surface, as requested during implementation.
- [x] Complete final headless and world validation.
- [ ] Manual glass/medium visual acceptance.

Existing `MobVatsAcceptance` and `MobVatsTest` saves contained both creative-fed media at capacity. Water: 6,000 mB; culture: 6,000 mB. The saved culture includes the actual Mixer composition (1,000 water + 250 experience in its formula). There are no retained migration/reconnection conflicts in these stores. Normal routes and existing fixture assertions already establish successful movement. The shared reason for stopping is capacity, rather than cow compatibility. The misleading combined tooltip and absent medium rendering made the result difficult to determine visually. This evidence applies to the inspected installations; it does not establish the state of an unidentified external save.

The creative-fed Vat inlet is active input, with an inward TAP at local (1,1,1), a riser TAP at (1,2,1), a glass shell TAP at (0,2,1), a glass buffer at (0,2,0), and a filtered roof Fill Valve at (1,3,1). The ordinary-water source is outside the inlet. The culture supply is the adjacent passive Mixer output. No production fixture correction or routing-mechanics change was required.

### Diagnostic precedence

Any successful candidate wins. Otherwise report the furthest operation stage reached:

1. Zero supply tanks in the normal connected handler: **No connected supply**.
2. Connected tanks all empty: **No fluid available**.
3. Fluid exists, but no candidate matches the valve filter: **Filter mismatch**.
4. At least one matching candidate reaches authoritative medium acceptance: **Migration/reconnection conflict**, then **Medium at capacity**, then **Incompatible existing medium**, following `VatMediumStorage.fillRejection()`.
5. A matching, legal candidate reaches transfer but its simulated source drain fails: **Transfer blocked or unavailable**.
6. Executed transfer: **Filling normally**.

The filter is deliberately applied in the valve operation, rather than hiding rejected samples in its handler. Other boundary handlers retain their filters. Diagnostics are cached during the existing scheduled transfer, including medium identity/amount/capacity, incoming identity and accepted mB per operation. Inspection only reads the cached fields; it performs no extra transfer, reservation or graph lookup. The decision used by `fill()` is also the decision used for rejection diagnostics. Unchanged diagnostics do not send extra block updates.

### Vat presentation

The unique Controller renders one cached translucent medium mesh. Side faces are inset behind all Vat glass, including glass conduit/buffer shells, and clipped vertically to the actual fill height. At half height through a window, its lower half is covered by liquid. Opacity is constant for the fluid; height coverage expresses filling. The top uses adjacent unit tiles with no overlapping area, and skips fully displaced interior cells. Empty stores produce no mesh. Over-capacity stores display only the enclosure's available height.

Server-compiled layer capacities are synchronized as presentation data; client rendering does not independently infer gameplay capacity. Geometry is invalidated by presentation updates and bounds/amount/capacity changes. The fluid texture and composition tint use the existing appearance path. Side and top liquid use alpha at most 160/255 to retain visibility into the Vat. No gameplay liquid blocks are placed.

## R-01: Mixer liquid opacity and lighting

- [x] Inspect working-fluid selection, recipe state, sync, texture UVs, fan winding, normals, bounds and rendering order.
- [x] Remove the arbitrary 210/255 alpha ceiling.
- [x] Sample surface and above-surface lighting, preserving fluid emission.
- [x] Add headless alpha/light regressions; retain existing state/mesh regressions.
- [x] Complete final validation.
- [ ] Manual above/side glass, gameplay-distance and darker-interior acceptance.

Mixer inputs remain physical until batch completion. Existing active-batch state and network tests verify that the real carrier remains available throughout processing, and disappears on completion when no carrier remains. A new invented storage/snapshot is unnecessary. The four creative-fed Mixer fixtures additionally count empty presentation frames during processing and verify real product conservation.

The renderer now uses the fluid's authored alpha without a ceiling (255 remains 255; authored translucency remains intact), while retaining the translucent atlas render type and composition-derived tint. Liquid lighting is sampled at its actual surface and immediately above it, taking the larger independent sky/block components as vanilla `LiquidBlockRenderer` does; luminous fluids retain their block light. The former renderer used the Controller block's light sample for the entire vessel. No full-bright floor is imposed on water. Polygon geometry, fan winding, texture coordinates, vessel radius/colors, rotor animation and recipe execution remain unchanged.

## Regressions and validation record

New `VatFillDiagnosticGameTests`: supply/empty/filter/capacity precedence; successful amount and partial overflow conservation; incompatible, migration and reconnection rejection; blocked-source drain; usable later candidate; finite water through a formed cow Vat; actual Mixer-executor compound output through an independent formed cow Vat; identity/composition/fractions/frequency persistence; read-only repeated inspection.

New `VatMediumRenderingGameTests`: half-covered window, dry window above the level, empty/full bounds, top tiling area, face winding, server/client level snapshot, displaced-layer height and omitted fully displaced surface cell.

New Mixer alpha/light regression complements existing polygon fan, level bounds, empty mesh, tint, active batch, network/save-load, and four creative-fed production tests.

All final checks passed:

| Check | Result | Evidence |
| --- | --- | --- |
| `gradlew.bat build` | PASS | `.cache/medium-liquid-build.log`; includes `verifyAiPlaceholderDevResources` |
| `gradlew.bat runGameTestServer` | PASS, all 729 required tests | `.cache/medium-liquid-gametest.log`; normal server shutdown |
| `buildTestWorld -PworldName=MobVatsAcceptance` | PASS at 5,000 ticks | `.cache/medium-liquid-acceptance.log`; saved all dimensions and exited |
| `buildTestWorld -PworldName=MobVatsMinimumXp -PacceptanceMinimumCowXp=true` | PASS at 49,771 ticks | `.cache/medium-liquid-minimum-xp.log`; saved all dimensions and exited |
| `git diff --check` | PASS | No whitespace errors |

Eight focused GameTests were added. The final full suite reports no required failures. During development, test-only assumptions were corrected: finite manual fluid insertion requires a passive port; composition-exact output requests must retain compound components; recipe reagent quantities and medium frequency must come from loaded definitions; mesh-area tolerances must account for distant test-world coordinates. None required changing production balance or routing.

| Medium assertion | Before | After |
| --- | --- | --- |
| Finite water through formed cow Vat | 0 medium; 2,500 mB inlet/buffer | 2,500 mB water medium; 0 buffer |
| Finite real Mixer output through independent cow Vat | 0 medium; one loaded recipe output in inlet/buffer | Exactly that output amount/composition in medium; 0 buffer |
| Partial capacity transfer | 1,000 / 1,500 mB medium; 1,000 mB supply | 1,500 / 1,500 mB medium; 500 mB supply |
| Creative water Vat, standard/extended | Initially empty | 6,000 / 6,000 mB water |
| Creative culture Vat, standard/extended | Initially empty | 6,000 / 6,000 mB culture with Mixer composition |
| Incompatible/full/conflicted rejection | Known existing medium and supply | Both unchanged |

All four creative-fed Mixers pass their sustained production, zero-empty-processing-frame, real working-fluid surface and conservation checks. Standard culture production retains 4,000 mB in its Mixer, 10,000 mB in the Vat/network (6,000 medium + 4,000 buffer), and disposes only the accounted remainder. Extended P-06 verifies the existing 250 mB XP culture batch, qualified composition, actual juvenile growth, and resolved enriched cow medium frequency 5.0. The fixtures continue to use their original active/passive boundaries and TAP routes.

Previous generated worlds were copied to ignored `.cache/medium-liquid-world-backups/` before regeneration. Temporary NBT inspection and vanilla source extraction are also ignored. The minimum-XP reports' visual-check sentence was updated after validation to describe the new renderer; assertions and world NBT were not altered. Required local build and world tasks retain the existing AI-placeholder packaging safeguards. No client was launched, and no commit, publish, release or deployment was performed.

Manual checks remain: partially filled and full Vat windows/top glass, visible continuous fill surface, transparency and depth sorting around specimens and glass conduits/buffers; water/culture/feed/general-filter/lava-filter Mixer texture and tint from above and the side, at normal distance, in daylight and darker interiors. Empty machines should display no invented liquid. Vessel/rotor geometry and placement remain unchanged. Headless validation establishes state and mesh correctness, not rendered pixel appearance.
