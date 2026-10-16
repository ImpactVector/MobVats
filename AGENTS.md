# Repository instructions

Before working, read and follow `../workspace/AGENTS.md`, relative to this file.
The `src` and `workspace` repositories must be sibling folders. If the workspace
checkout is missing, report that the portable project instructions and references
are unavailable; retain the testing restrictions in `README.md`.

- Current mod repository and all authored code belong here, in the workspace's `src/` directory.
- Follow ExtremeReactors2's controller, part-type, individual entity, and client package structure.
- Compilation, unit tests, and autonomous headless GameTests are allowed. GameTests must handle setup, simulated interactions, and verification themselves. Do not take over the mouse/keyboard, use browser-style screenshot loops, or perform manual Minecraft client testing. The user handles client play and visual acceptance. Do not launch client smoke/reload tasks unless specifically requested. This preference was clarified on October 3, 2026.
- Keep IntelliJ and Minecraft generated files ignored.
- The standard Gradle wrappers default `GRADLE_USER_HOME` to the writable `../workspace/.cache/gradle` directory. Do not override it with the protected `../.codex/gradle` cache.

## Consistent Windows Java 21 validation

Use this exact command form for routine agent Gradle work (from any directory):

```powershell
& 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -File C:\Projects\MobVats\src\tools\gradle-java21.ps1 -Mode version
```

Replace only the mode with one of:

| Mode | Fixed Gradle invocation |
| --- | --- |
| `version` | `--version` (verify launcher and daemon JVM) |
| `format` | `spotlessApply` |
| `check` | `spotlessCheck check` (CPU/resource checks, no game server) |
| `build` | `spotlessCheck build` |
| `gametest` | `runGameTestServer` (autonomous headless tests) |
| `validate` | `spotlessCheck build runGameTestServer` |

The launcher fixes the working directory to this repository and the Gradle cache
to `../workspace/.cache/gradle`. It selects an installed Java 21 JDK, preferring
that cache's `jdks`, then the user's `.gradle/jdks`, then a Java 21 `JAVA_HOME`.
It fails if none exists, preserves Gradle's exit status, restores the caller's
environment, and rejects extra arguments. Do not prepend environment assignments,
add a manual `-Command` wrapper, or append shell commands/redirection to an approved call.
Keep inspection commands separate so the approval prefix stays consistent.

Finish Java edits before validation. Run `format`, inspect the formatting diff,
then run `validate` once when the slice requires the full build and GameTests.
Use `check`/`build` for smaller changes where GameTests add no relevant coverage.
Do not rerun completed checks unless a subsequent change or failure warrants it.

The host-local reusable allow rule is
`C:\Users\Dan\.codex\rules\mobvats-gradle.rules`; its reviewable source is
`tools/codex-mobvats-gradle.rules`. It permits only the absolute PowerShell
executable, `-NoProfile -File`, this launcher path, and the six listed modes.
It also recognizes the execution tool's PowerShell `-Command` wrapper, but only
when the entire wrapped command is one of the six exact canonical calls above.
It does not approve arbitrary PowerShell, arbitrary Gradle tasks/options, client
runs, publication, or another checkout. The launcher/build files are trusted
executable project code: review changes to them before using the allowance.

When the sandbox cannot connect to Gradle's localhost daemon, retry the same
canonical command with `sandbox_permissions="require_escalated"`; the installed
rule can allow it without another interactive prompt. Do not broaden permissions
or revive old `.codex/gradle` command approvals. A session already open during
installation may need a new session to pick up the installed rule.
On another host/checkout, review and adapt the absolute paths before installing
the rule; `AGENTS.md` alone does not grant executable permissions.

## Java formatting

Follow `docs/CODE_STYLE.md`. Spotless 8.10.4 / Palantir Java Format 2.102.0
apply/check all authored Java, including tests, benchmarks and the geometry
exporter. Use a Java 21 Gradle runtime. For authorized Java changes, run
`spotlessApply`, inspect the mechanical diff, run `spotlessCheck`, then
compile/test. Do not mix semantic refactoring with formatting. `check` and `build`
enforce formatting without rewriting source.

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
