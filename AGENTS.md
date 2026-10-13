# Repository instructions

Before working, read and follow `../workspace/AGENTS.md`, relative to this file.
The `src` and `workspace` repositories must be sibling folders. If the workspace
checkout is missing, report that the portable project instructions and references
are unavailable; retain the testing restrictions in `README.md`.

- Current mod repository and all authored code belong here, in the workspace's `src/` directory.
- Follow ExtremeReactors2's controller, part-type, individual entity, and client package structure.
- Compilation, unit tests, and autonomous headless GameTests are allowed. GameTests must handle setup, simulated interactions, and verification themselves. Do not take over the mouse/keyboard, use browser-style screenshot loops, or perform manual Minecraft client testing. The user handles client play and visual acceptance. Do not launch client smoke/reload tasks unless specifically requested. This preference was clarified on October 3, 2026.
- Keep IntelliJ and Minecraft generated files ignored.

## Java formatting

Follow `docs/CODE_STYLE.md`. Spotless 8.10.4 / Palantir Java Format 2.102.0
currently apply/check only the seven enrolled paths in `gradle/spotless-trial.txt`.
Use a Java 21 Gradle runtime. For authorized Java changes, enroll only the touched
file or approved batch, run `spotlessApply`, inspect the mechanical diff, run
`spotlessCheck`, then compile/test. Do not format the full repository or mix
semantic refactoring with formatting. Full enforcement follows baseline migration.

## AI PLACEHOLDER ART — HARD RELEASE BLOCKER

This repository permits AI-generated visual assets only as explicit development
placeholders.

Before any public release, publish, public artifact, or request to prepare the mod
for public distribution:

- inspect `docs/history/2026-10-12-ai-placeholder-retirement.md`
- verify no `AI_PLACEHOLDER` assets remain or are referenced
- run `./gradlew verifyNoAiPlaceholderArt publicReleaseValidation`

If any AI placeholder remains, STOP and tell the user the release is blocked.
Do not publish, release, upload, or work around the guard unless the user has
replaced/removed all placeholder assets.

Do not silently reclassify AI placeholder art as final art.
Human-authored or properly licensed replacement art must be explicitly substituted.

Keep all AI sources in `dev/ai-placeholder-art/`, track every source and derivative
in the manifest, and retain source hash history after replacement/removal.
Local `build/libs/` JARs containing placeholders are development-only artifacts.
Any future task that creates or delivers public distribution must depend on
`verifyNoAiPlaceholderArt`, including custom tasks whose names/groups do not match
the automatic publish/release/upload/distribute/deploy conventions.
Never attach this release blocker to ordinary local builds, GameTests, or runClient.
