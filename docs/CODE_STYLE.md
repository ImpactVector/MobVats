# Java formatting and staged adoption

Spotless **8.10.4** runs Palantir Java Format **2.102.0**, explicitly using the
`PALANTIR` style. Versions are pinned in `build.gradle` and
`gradle/spotless.gradle`. The supported and tested combination is Java 21,
Gradle wrapper 9.2.1, ModDev 2.0.148, Minecraft 1.21.1 and NeoForge 21.1.252.
ZeroCore2 remains the published 1.21.1-2.4.18 dependency.

Run Gradle itself on Java 21 for formatting, as well as compiling with the Java
21 toolchain. A Java 17 launcher can build with the downloaded compiler but does
not establish Java 21 formatter support. This workspace used Temurin
21.0.12.1+1 at the following local path; other machines should use their own
Java 21 installation:

```powershell
$env:JAVA_HOME='C:/Projects/MobVats/.codex/gradle/jdks/eclipse_adoptium-21-amd64-windows.2'
$env:GRADLE_USER_HOME='C:/Projects/MobVats/.codex/gradle'
```

The first invocation needs access to Gradle Plugin Portal and Maven Central to
resolve the pinned dependencies. Once cached, the validated commands work
offline. Do not commit machine-specific Java/cache paths.

## Commands and current enforcement

From the source repository (`C:/Projects/MobVats/src` in this workspace):

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck
.\gradlew.bat build
.\gradlew.bat proceduralArtPreview compareProceduralArtBaseline --offline
.\gradlew.bat runGameTestServer
git diff --check
```

Until migration is complete, **apply and check cover exactly the seven paths in
[spotless-trial.txt](../gradle/spotless-trial.txt)**. The normal `check`/`build`
gate checks this enrolled set only. Build never applies formatting.

| Trial file (under `net/impactvector/mobvats`) | Representative coverage |
| --- | --- |
| `src/main/java/.../gamecontent/mob/CapturedSpecimen.java` | Record, compact constructor, annotations, NBT strings and Javadocs |
| `src/main/java/.../gamecontent/fluid/compound/CultureFouling.java` | Java 21 `List.getFirst()`, generics, method reference, exception handling |
| `src/main/java/.../gamecontent/multiblock/conduit/ConduitGeometry.java` | Records, compact constructors, switch expressions, lambdas, geometry |
| `src/main/java/.../gamecontent/multiblock/conduit/FluidBufferEntity.java` | Block entity, NeoForge tank subclass, lifecycle methods, method reference |
| `src/main/java/.../gamecontent/multiblock/vat/VatPortCapabilities.java` | Long capability declarations, NeoForge annotations and lambdas |
| `src/main/java/.../gametest/ConduitGeometryGameTests.java` | GameTest discovery, Java 21 first/last list access, ZeroCore API usage |
| `src/test/java/.../render/ConduitArtGeometryTest.java` | CPU tests, nested loops, generic collections and geometry verification |

Read-only diagnostics for the eventual full scope:

```powershell
.\gradlew.bat spotlessCheck -PspotlessScope=all
```

This is expected to fail on unmigrated files. It is not a mandatory gate yet.
Full-scope apply tasks explicitly reject execution during adoption. Do not use
an init script or edit the configuration to bypass that restriction for a mass
pass. Expand the trial manifest only for an authorized, reviewable batch.

## Scope and exclusions

The eventual formatter targets authored Java under `src/**/*.java` and
`benchmarks/src/**/*.java`, plus `tools/procedural-art/GeometryExport.java`.
This covers `main` (including development GameTests), `test`, any authored
`manualClient` Java, future custom source sets under `src`, the independent
benchmark build, and the custom JavaCompile geometry exporter. Benchmark
formatting is driven from the root wrapper; its standalone build does not
currently enforce the root policy.

Directories named `generated` within those trees are excluded. Build-generated
sources (`build/generated/**`, `build/tmp/**`, `build/moddev/**`), all other build
outputs, `.gradle`, `.cache`, external `.codex` caches/references, `examples`, and
downloaded repositories are outside the positive target roots. New authored
source roots outside these paths require an explicit configuration update.
JSON artwork and generated Minecraft resource/model JSON are never targets.

## Mechanical formatting policy

Palantir owns indentation (four spaces), its 120-column wrapping policy,
statement layout, whitespace, existing brace layout and lambda formatting.
Long indivisible strings/comments can exceed the preferred width. The formatter
does not add missing braces; do not add them manually in formatting-only work.
Sources use UTF-8 and LF, consistent with `.gitattributes`. Javadoc reformatting
is disabled; comments and their meaning must survive patch review.

The Spotless Palantir integration inherently sorts ordinary/static imports and
removes unused imports. Those small import changes are part of the trial and
must compile. Wildcard imports remain wildcard imports. No extra import-order,
wildcard expansion, fully qualified type shortening, cleanup/refactoring or
static-analysis steps are enabled. A later import-policy change needs its own
review and compilation evidence.

Preserve public APIs, serialized/NBT identifiers, resource locations, literal
values, exception handling, evaluation order, comments, gameplay geometry and
method behavior. Do not rename, extract methods, reorganize packages, change
fixtures or update golden hashes while formatting.

For future Codex Java edits: check the manifest first. For an enrolled file,
edit, run `spotlessApply`, inspect the patch, run `spotlessCheck`, compile/test,
then commit only when authorized. For an unenrolled file, enroll the specifically
authorized file/batch in the manifest and document that expansion; do not
silently leave touched Java unformatted or authorize unrelated migrations.

## Migration plan (not executed)

The pre-format source revision is `6bc79442f489637689e7eee57d57c52bd1f1f472`.
Keep that revision available for comparison and blame; record each later batch's
parent SHA. No tag or commit was created in this installation slice.

1. Small value objects and shared utilities.
2. Conduit and resource infrastructure.
3. Vat and Mixer processing.
4. Controllers and block entities.
5. Client rendering.
6. Tests, acceptance builders, benchmark sources and the build-only exporter.

For each authorized batch, append its exact paths to the enrollment manifest,
apply only that expanded scope, inspect the mechanical/import diff, apply a
second time and compare hashes, check, compile and run relevant tests. Run art
checks when geometry/export inputs change. Keep formatting-only commits separate
from semantic refactoring. Record a batch's validation independently of existing
acceptance-world issues.

After all authored Java is migrated and a full diagnostic check passes, replace
the manifest target with `authoredJava`, make full checks mandatory, and retire
the temporary scope/property/apply guard together. Until then there is no
permanent legacy-source exclusion. The exact remaining inventory is recorded in
[CODE_STYLE_REMAINING.txt](CODE_STYLE_REMAINING.txt); it is an inventory, not a
formatter exclusion list.

## Installation evidence and limitations

See [CODE_STYLE_VALIDATION.md](CODE_STYLE_VALIDATION.md) for the current baseline,
trial, idempotence, art hashes, toolchain and final validation results.
The historical audit's parser warning was resolved by the JSON extraction and
compiled exporter before this installation. Formatting `ConduitGeometry` must
still pass the runtime/exported-dimension tests and immutable art baseline.

The two acceptance-world failures in [PROCEDURAL_ART_VALIDATION.md](PROCEDURAL_ART_VALIDATION.md)
remain outstanding. Their existing logs and report are preserved. No client or
acceptance-world generation is required or performed by this tooling slice.
Gradle 10 and other JDK versions are not certified by these results.

References: [Spotless 8.10.4 plugin](https://plugins.gradle.org/plugin/com.diffplug.spotless/8.10.4),
[Spotless Gradle guide](https://github.com/diffplug/spotless/blob/main/plugin-gradle/README.md),
[Palantir 2.102.0 release](https://github.com/palantir/palantir-java-format/releases/tag/2.102.0),
[Palantir Java 21 runtime guidance](https://github.com/palantir/palantir-java-format#java-21-support),
[Spotless Palantir import integration](https://github.com/diffplug/spotless/blob/main/lib/src/palantirJavaFormat/java/com/diffplug/spotless/glue/pjf/PalantirJavaFormatFormatterFunc.java).
