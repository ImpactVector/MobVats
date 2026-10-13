# JSON procedural art extraction validation — October 13, 2026

Generator: 1.14.0. Pre-extraction baseline: generator 1.13.0 at
`6fa092550e14028eec533de883b5ef71bd67cb38`.

| Check | Result |
| --- | --- |
| Original `proceduralArtPreview --offline` before edits | Passed; baseline generated before extraction |
| SHA-256 baseline versus regenerated outputs | **233/233 PNGs and 453/453 model/resource files identical**; zero additions, removals or changed paths |
| Final `proceduralArtPreview build compareProceduralArtBaseline --offline` | Passed, including CPU geometry tests, resource ownership and JAR verification |
| Repeat `proceduralArtPreview --offline` | Passed; all 11 tasks UP-TO-DATE, including gameplay/exporter compilation, export, generation and preview |
| `testProceduralArt` | Passed; independent repeat generation, all existing raster/resource regressions, overlays and collision fixtures |
| JSON editability | Passed; isolated skull pixel, T1 base color, fixed fluid color and casing border edits; exact expected affected PNG sets and all unrelated bytes preserved |
| JSON errors | Passed; schema version, unknown primitive, required field, color, row length, symbol, negative dimensions, bounds, unresolved reference and recipe cycle rejection |
| Geometry export | Passed; explicit schema/units, runtime conduit/buffer comparisons, formed geometry tests, no task cycle |
| Packaging | Passed; all 233 PNG hashes and processed/JAR resource hashes match provenance; exporter class absent from JAR; protected base resources preserved |
| AI guard rejection fixtures | Passed; active ledger, retained source, renamed fingerprint, dangling reference and invalid ledger rejected |
| `verifyNoAiPlaceholderArt publicReleaseValidation --offline` | Passed on the existing retired-placeholder ledger; no publication/upload performed |
| `runGameTestServer --offline` | **All 766 required GameTests passed**, 1.374 minutes; `.cache/gametest/logs/latest.log` |
| Standard `buildTestWorld --offline` | Initial run failed at O-09 Armadillo brush; unchanged retry failed culture consumption at the production observation deadline. See below. |
| `git diff --check` | Passed |

## Hash evidence

The source-controlled
[pre-extraction.json](../tools/procedural-art/baseline/pre-extraction.json) contains
all 686 original resource paths and SHA-256 hashes. Its SHA-256 is
`ce5bc5ed435e1d79aeb9887261de29d7f95cae81cba8332f57bd7b100ee6f5a2`.
`compareProceduralArtBaseline` independently recomputes hashes from final output
and checks the exact path set. PNG encoding was unchanged; no decoded-pixel
exception, palette redesign, altered seed or updated golden hash was used.

The generated metadata manifest changes intentionally to identify the JSON
artwork sources, exporter source hash, explicit geometry contract, and generator
version. All generated Minecraft-facing PNG/model/resource bytes are unchanged.
The five biological golden PNG hashes and historical provenance are retained.

## Acceptance-world limitation

The standard generator reached its 53,000-tick production observation deadline
and failed final operator verification at `OperatorVatFixtures.verify`, O-09
Armadillo brush. The Tool Port no longer held its supplied brush at verification.
The failure is recorded in `run-world-generator/logs/latest.log` and
`run/saves/MobVatsTest/generation-failed.txt`; no completion marker was produced.
This is not a successful acceptance-world run.

No gameplay, process timing, production rates, operator fixtures or verification
thresholds were changed to make this check pass. The untouched-HEAD archive under
`build/tmp/acceptance-baseline/` passed standard world generation with 10 mirrored
fixtures and 9 operator Vats after 5,000 observation ticks. Its mod configuration
matches the working checkout. This does not reproduce the failure; differing
production observation durations suggest an intermittent acceptance-fixture
issue. One unchanged standard retry failed a different production assertion:
`Culture batches did not consume 1000 mB water + 250 mB XP` at the observation
deadline. Its log is `build/reports/procedural-art/acceptance-retry.log` and the
world records its diagnostics in `acceptance.md`/`generation-failed.txt`.

All 524 compiled gameplay class files are byte-for-byte identical to the
untouched revision's compiled output. The processed resource path sets also match:
46 source JSON files differ only in whitespace/line endings from the Git archive;
all processed resources are semantically identical except the intentionally
updated procedural provenance manifest. No causal connection between the acceptance
failures and the PNG infrastructure refactor was established. Standard
acceptance-world validation remains outstanding; do not claim this slice passed
every acceptance check or change gameplay behavior to conceal these failures.

The initial failure log and exception are preserved in
`build/reports/procedural-art/acceptance-initial-failure.{log,txt}` because standard
world generation resets the disposable world and replaces its current log.

There was no client launch, manual mouse/keyboard testing, formatting sweep,
commit, push, publish or deployment. Missing legacy procedural documents were
replaced with current guidance in `PROCEDURAL_ART.md`; the existing historical
records remain intact.
