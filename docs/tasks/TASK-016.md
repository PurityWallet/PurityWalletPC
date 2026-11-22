# TASK-016: Update the bundled Temurin runtime

## Scope

- [x] Pin the release, test and dependency-verification workflows to Eclipse Temurin `25.0.5.1+2`.
- [x] Update the local build instructions and SDKMAN configuration for the same JDK.
- [x] Keep Gradle `9.1.0` and the pinned Drongo revision.

## Validation

All 14 Python packaging checks passed. Adoptium resolves the exact build for all five packaging targets. A local run of the pinned `setup-java` action installed Temurin `25.0.5.1+2` on Apple Silicon and reused it on a second run.

On the initial `1.1.0` base, Apple Silicon `:jpackageImage`, all 378 regression tests and all seven desktop tests passed. The desktop tests used JavaFX's Headless platform and a temporary cache. The packaged JVM reported build `25.0.5.1+2-LTS`, and the packaged launcher reported `PurityWallet 1.1.0`. Hosted CI will validate the upgrade on the current `1.1.1` base.
