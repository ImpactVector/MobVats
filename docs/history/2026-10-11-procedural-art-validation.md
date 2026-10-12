> Historical record. Original: `docs/PROCEDURAL_ART_VALIDATION.md`. Current reference: [05-PROCEDURAL-ART.md](../05-PROCEDURAL-ART.md).
> Archive date basis: Earliest explicit report date; later appended findings retain their own dates. Historical commands, measurements, paths and implementation claims apply to their recorded revision.

# Production integration validation — October 11, 2026

Generator version: 1.3.0. Production bindings are recorded in
`tools/procedural-art/bindings.json`; the full binding/selection/acceptance report
is at the start of `2026-10-11-procedural-art.md`.

- `proceduralArtPreview --offline`: passed; preview and game use the same 44 PNGs.
- `build` and final `proceduralArtPreview build --offline`: passed, including
  deterministic art contracts, tier/fixed-color/direction checks, packaged model
  and blockstate resolution, processed runtime PNG hashes, unique JAR entries,
  development hook exclusions, unchanged base art and remaining AI bindings.
- `runGameTestServer`: passed, all 734 required tests. Includes the new seven-port
  direction projection/save-load/mode-independence/entity-retention check and
  existing capability/lifecycle tests. No transfer or capability implementation
  changed.
- Repeated `proceduralArtPreview --offline`: generator and preview UP-TO-DATE.
- `processResources --offline --dry-run`: generation tasks precede processing.
- `verifyNoAiPlaceholderArt publicReleaseValidation --offline`: failed as expected
  with HARD PUBLIC RELEASE BLOCKER. Controller/shaft/impeller and source-only
  motors remain flagged; retained source history and shipped fingerprints are
  still rejected. The guard implementation and its regression fixtures remain
  active, without a generated-directory exception.
- `git diff --check`: passed.

Local logs: `.cache/art-{preview,final-build,gametest,up-to-date,task-graph,release-guard}.log`.
Development artifact: `build/libs/mobvats-0.1.0.jar` (contains remaining AI art,
not suitable for public distribution).

Manual visual acceptance is pending. Use `/mobvatsArtFixture` from an empty area
in a user-launched development client; no existing acceptance world reset is
needed. Clean casing, four directed ports, glass and optional loaded Pipez are
production models. Weathered and undirected examples remain in the preview
because no existing real-part selector supports them. No graphical client,
commit, publication, release or deployment was performed.

## Historical validation

# Procedural art validation — October 11, 2026

## Azure and two-pixel port markings

Generator version: 1.2.0. Fixed fluid color is exactly Azure `#168BFA`; item
yellow remains `#E3BC45`. Four-pixel diagonals are unchanged. Cardinal strokes
are two pixels including the diagonal endpoint, giving 24 indicator pixels for
Input/Output and 16 for None.

`gradlew proceduralArtPreview testProceduralArt --offline --console=plain`
passed. Tests verify all six variants, four-way rotational symmetry, correct
inner/outer endpoint attachment, no cardinal run exceeding two pixels, gaps
between motifs, unchanged center, exact RGB in grayscale/T1/T2/T3, and
deterministic output. `git diff --check` passed.

The regenerated contact sheet was inspected at its native and enlarged views;
Azure markings remain distinguishable against verdigris and iron, and opposing
corner motifs have clear gaps. Final gameplay visual acceptance remains pending.
All 16 casing PNGs matched their pre-change SHA-256 hashes. No casing recipe,
production binding, block model, gameplay code, or registration was changed.
No commit, publication, or release was performed for this refinement.

Local log: `.cache/port-markings-refinement.log`.

## Corner markings and casing refinement

Generator version: 1.1.0. This refinement changes only authored art recipes,
development generator/preview code, contract tests, and documentation. Palettes,
gameplay, registrations, models, and production bindings are unchanged.

- Generated 44 deterministic textures: four casing and seven socket variants
  across grayscale master/T1/T2/T3. The six marked socket variants are
  Fluid/Item × None/Input/Output.
- Art contracts passed for identical diagonals, inner/outer endpoint arms,
  four-corner reflection and rotation symmetry, exact fixed blue/yellow across
  tiers, neutral-center clearance, unchanged metal outside the marking masks,
  semantic palette mapping, sparse repeatable scratches and seed variation.
- Added deterministic clean/weathered 3×3 and 7×7 wall previews for all three
  tiers, with native and nearest-neighbor views. All twelve wall PNGs reproduce
  byte-for-byte in independent test generations.
- Regenerated and inspected the overview and wall contact sheets. Reduced the
  recessed-border contrast after the first wall inspection. Corrected the
  preview coordinate seed selector to vary tiles across rows. Final visual
  acceptance remains with the user.
- `verifyProceduralArtResources` passed: all 44 PNG hashes match packaged
  provenance and no machine model binds the prototype namespace.
- `verifyAiPlaceholderDevResources` passed: existing placeholder bindings and
  original PNGs remain unchanged. The release guard script was not modified.
- No gameplay code changed, so the prior headless GameTest result below is
  retained as historical evidence; GameTests were not rerun for this art-only
  refinement. No client, commit, publication, or release was performed.

Commands: `gradlew generateProceduralArt proceduralArtPreview testProceduralArt
verifyProceduralArtResources verifyAiPlaceholderDevResources testAiPlaceholderGuard
--offline --console=plain`. Log: `.cache/procedural-art-refinement.log`.

## Original Slice 1 validation

Scope: Slice 1 infrastructure and prototype artwork. No production bindings,
registrations, mechanics, or existing source PNGs were changed by this slice.

| Check | Result |
| --- | --- |
| `generateProceduralArt proceduralArtPreview testProceduralArt --offline` | Passed; 36 deterministic 16×16 PNGs, HTML, contact sheet, and provenance. |
| `build --offline` | Passed; art contracts, packaged-resource verification, existing asset preservation checks, development hook exclusion, and existing render buffer regression passed. |
| `runGameTestServer --offline` | Passed; all 733 required tests, server shut down normally, exit 0. No client launched. |
| Repeated generation/preview/test command | All three tasks `UP-TO-DATE`; unchanged inputs cause no PNG regeneration. |
| `testAiPlaceholderGuard --offline --rerun-tasks` | Passed all six existing rejection/clean-ledger fixture cases. |
| `verifyNoAiPlaceholderArt publicReleaseValidation --offline` | Expected exit 1 at the unchanged hard blocker: active AI ledger rows, retained sources, references, and fingerprints. Public validation did not proceed. |
| `git diff --check` | Passed. |
| Contact sheet inspection | Initial sheet inspected, overlapping labels shortened, regenerated sheet inspected. Tier structure and directional geometry are consistent; wear is sparse. User visual acceptance remains pending. |

The JAR verifier found all 36 PNGs and the provenance manifest, matched every PNG
against its manifest SHA-256, confirmed 16×16 dimensions, and rejected any model
binding to the prototype namespace. The existing checks also confirmed six
development placeholder bindings, the unchanged base PNGs, and seven explicitly
preserved resource hashes. No generator code is packaged for players.

The GameTests ran after initial generator integration; subsequent changes were
limited to development-tool validation and contact-sheet labels. The final build
and generator contracts passed after those refinements.

Local logs (ignored): `.cache/procedural-art-build.log`,
`.cache/procedural-art-incremental.log`,
`.cache/procedural-art-guard-regression.log`,
`.cache/procedural-art-release-guard.log`, and
`.cache/gametest/logs/latest.log`.

Existing uncommitted Vat rendering and test work was present before this task.
It was preserved; the only added line in the existing `build.gradle` applies
`gradle/procedural-art.gradle`. No commit, publication, release, or deployment
was performed. Local JARs remain development-only while AI placeholders exist.
