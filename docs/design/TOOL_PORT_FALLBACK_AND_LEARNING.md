# Tool Port interaction fallback and bounded learning — design

Status: **proposed, not implemented**. Recorded 2026-10-24. This document records the agreed direction; it is not a gameplay or config contract until implemented.

## Intent

Preserve explicitly configured mob tool interactions and the existing transaction-safe executors, while letting an unfamiliar Tool Port item attempt a real vanilla/NeoForge-style interaction against the specimen's **current reconstructed state**. Do not require discovery of every modded interaction during registry scanning or syringe capture. Never run ordinary mob AI as part of probing.

## Player controls

- Tool Ports expose a **right-click/use vs. left-click/attack** selector. Right-click dispatch follows the actual interaction path; left-click follows an appropriate attack/hit path, **not** the right-click dispatch renamed. Reuse the existing Sword harvest mechanism where applicable; never double-attack or double-commit drops.
- When no explicit configured interaction applies, attempt a **generic fallback** with the mounted tool through supported detached/specimen transaction machinery. Configured interactions take priority, including their cooldown, tool selectors and action semantics.
- Expose a configurable **X unsuccessful-attempt discovery limit** for unknown item/specimen/action pairings (provisional default: **50**). Enforce a bounded attempt cadence so unsupported combinations are not retried every tick. Precise GUI placement and interval policy are implementation details.
- Provide a **manual Retry** action. Removing/reinserting or replacing the mounted tool also resets the discovery attempt budget; an actual change of encoded specimen resets pairing identity.

## Proven / dormant behavior

- An unknown pairing begins **unproven**. Attempts that produce no meaningful supported result consume the discovery budget. After X unsuccessful attempts, mark it **dormant** and stop automatic attempts until reset.
- **One meaningful committed result establishes proven status**. Once proven, the pairing keeps attempting normally even after arbitrarily many later failed or no-output attempts. Do not revoke proof or apply the initial discovery cutoff again.
- Count a meaningful result from committed item/fluid output, legitimate tool consumption/damage/replacement, or an allowlisted persisted specimen-state change; a bare SUCCESS with no change does not by itself prove productive behavior. Distinguish PASS, successful-but-no-change, and rejected/unsupported attempts diagnostically. Only committed work proves a pairing; blocked output capacity and unavailable resources are not proof or discoveries.
- Persist **attempt count, proven/dormant state, and pairing identity** over chunk unload/reload and server restarts. This persistence is **part of the intended feature**, not deferred. Scope it to the Tool Port, specimen identity, held item identity and click mode; avoid mistakenly inheriting learned state across specimen replacement or changed tool/action. Keep storage bounded and avoid unbounded histories.
- A change of config may invalidate/reassess pairing state as appropriate. Reloaded definition rules are authoritative; no special protection from admin-authored changes is needed.

## Transaction and compatibility boundaries

- Try real handlers for the current state as far as the existing safe executor permits: NeoForge entity interaction event, entity interaction and item interaction for use/right-click. Random-chance outcomes are resolved by actually running that interaction, not by inventing probabilities or substituting historical drops.
- Preserve existing detached-world safety: don't execute on the actual Vat occupant as a live AI entity; reject incompatible world changes, arbitrary entity spawns and unallowlisted specimen changes. Some modded tools requiring ServerPlayer or ServerLevel, or nonstandard damageable-item behavior, may need registered adapters.
- Keep explicit definitions and deterministic resource accounting ahead of fallback. Avoid duplicate attacks, double-consumption or loss/duplication when commit fails. Failed attempts must not damage/consume the real mounted tool.

## Future possibility: interaction outcome statistics

**Deferred, not required for the fallback.** A later Woot-like observation/diagnostic system could record bounded outcomes (PASS, no-change, item outputs, item/tool changes, state changes, unsupported) and their frequencies over 50–100+ attempts. It should not infer a fixed drop table or replace executing stateful/random modded interactions. Do not implement outcome histograms, population-level learning, or a global compatibility database as part of the present Tool Port feature.

## Acceptance cases for eventual implementation

1. Unknown right-click tool with a deterministic supported effect succeeds, commits atomically and becomes proven.
2. Unknown tool always returning PASS becomes dormant after configured X attempts; it stays dormant across save/unload/restart.
3. A rare random interaction succeeds before X and remains proven through more than X subsequent misses.
4. Retry and tool removal/reinsertion restart unproven discovery; changing specimen, item or click mode does not carry over unrelated proof.
5. Output backpressure and rejected unsafe effects cannot commit phantom products or damage the actual mounted tool.
6. Left-click uses the correct attack semantics without duplicating existing Sword harvest.
7. Explicit configured interactions continue behaving as before.
