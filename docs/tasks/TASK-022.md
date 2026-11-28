# TASK-022: Update the bundled Temurin runtime

## Scope

- [x] Pin the three jobs in the consolidated CI workflow, the manual Package workflow and the dependency verification action to Eclipse Temurin `25.0.5.1+2`.
- [x] Update the local build instructions and SDKMAN configuration for the same JDK.
- [x] Keep Gradle `9.1.0` and the pinned Drongo revision.

## Validation

Adoptium resolves the exact build for all five packaging targets. A local run of the pinned `setup-java` action installed Temurin `25.0.5.1+2` on Apple Silicon and reused it on a second run.

Apple Silicon DMG validation passed on the `1.1.1` base, including a read-only mount, the ad hoc signature, the launcher version and the SHA-256 checksum. The packaged JVM reported build `25.0.5.1+2-LTS`. The `1.2.0` base uses the consolidated CI workflow for Linux tests, Windows x64 MSI and Apple Silicon DMG.
