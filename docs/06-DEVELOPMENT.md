# 06 — Development

## 6.1 Workspace and dependencies

The mod repository is `src/`, beside `workspace/`, which holds portable instructions and references. Commands below run from the mod repository root. Read `AGENTS.md` and `../workspace/AGENTS.md` before work. Optional local upstream assets live outside the mod implementation; follow the workspace download instructions if needed.

Java 21 is required for compilation and Minecraft. The configured Gradle toolchain resolver can download it; Gradle itself can start under Java 17. Use the included wrapper. Versions in `gradle.properties` / `build.gradle` pin Minecraft 1.21.1, NeoForge 21.1.252 and external ZeroCore2 `curse.maven:zerocore-247921:6648746` (1.21.1-2.4.18). This is the configured dependency, not a claim about the newest upstream release. No ZeroCore classes are bundled.

Jade is compile-only. `manualClientRuntime` supplies Jade, JEI, Pipez and Building Gadgets 2 for the operator's normal client only; tests/world generation use the required mod environment. Optional runtime versions come from `build.gradle`.

## 6.2 Build and verification commands

```powershell
.\gradlew.bat build
.\gradlew.bat testAiPlaceholderGuard
.\gradlew.bat verifyNoAiPlaceholderArt publicReleaseValidation
git diff --check
```

`build` compiles, generates/processes resources, packages the mod and runs `check`, including CPU render/geometry and art/provenance/JAR hook exclusion checks. `publicReleaseValidation` simulates a guarded public entry point without publishing. No successful validation command authorizes a release.

The standard wrappers default to the ignored, writable sibling cache `../workspace/.cache/gradle`. Normal build commands remain unchanged:

```powershell
.\gradlew.bat build
```

An explicit `GRADLE_USER_HOME` overrides this default. Do not select `../.codex/gradle`, because agent configuration directories are protected. Configure a Java 21 Gradle runtime through `JAVA_HOME` or machine-local `org.gradle.java.home` in the cache's `gradle.properties`. The cache location must be selected before Gradle starts; project `gradle.properties` does not relocate Gradle User Home.

Offline mode requires cached dependencies. Use `tasks --all` to inspect the actual wrapper task inventory. Keep generated caches, logs, IDE files and worlds ignored.

## 6.3 Configuration examples and reloads

See [configuration samples](examples/README.md) for Mixer, culture, generic resource and passive/vanilla profiles. Samples illustrate schemas; they are not runtime configuration and are not automatically loaded from `docs/`. Copy/adapt them deliberately into the appropriate server config directory.

`/mobvats reload_passive_mobs` reloads specimen/culture definitions; `/mobvats reload_mixer` reloads Mixer recipes and compound names. Defaults are copied only when absent and authored overrides remain intact.

The development `build` task invokes `invalidateDevMixerDefaults`, deleting only `run/config/mobvats/mixer/_defaults.json` so next startup can restore the current bundled Mixer defaults. It refuses linked/non-file paths and leaves sibling overrides intact. This is local build housekeeping, not a production or startup rewrite.

## 6.4 Resource and world tools

`tools/generate_resources.py` and `tools/generate_shell_families.py` are separate authored resource-maintenance scripts; do not run them merely to update prose. Current procedural resource integration is owned by Gradle as described in [art](05-PROCEDURAL-ART.md). Generated resources/models must not be manually patched.

```powershell
python tools/inspect_saved_vats.py "path/to/world"
python tools/verify_unified_world.py "path/to/world"
```

Use an installed Python interpreter; these read Anvil data for diagnostics. Close/save Minecraft before saved-world inspection. The unified checker rejects obsolete block/item IDs and validates material/mode properties while allowing existing family adapter entity IDs.

## 6.5 Operator workflow

```powershell
.\gradlew.bat prepareClientRun
.\gradlew.bat buildTestWorld -PworldName=MobVatsDev
.\gradlew.bat runClient
```

`prepareClientRun` resolves/prepares without launching the client. `buildTestWorld` resets its selected disposable world; read [testing](07-TESTING.md) before using it. `runClient` is for the human operator. Agents may compile, run unit tests and autonomous headless GameTests; client smoke/reload tasks require a specific request. No agent mouse/keyboard or screenshot-loop acceptance is permitted.
