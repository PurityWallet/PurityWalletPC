# TASK-023: Bring in upstream Sparrow batch 1 (bundled runtime libraries)

REQ-018 batch 1. The analysis of all upstream changes is in the [migration report](../migration-report.md#upstream-sparrow-comparison--2026-11-28-req-018).

## Scope

- [x] Add packaging checks for the batch 1 library versions and the macOS texture size option.
- [x] Cherry-pick `21b4032`: Tor 0.4.8.21 → 0.4.9.5 (`io.matthewnelson.kmp-tor` 409.5.0).
- [x] Cherry-pick `8ea8e30`, `f13a4d9`, `114389b` and `d0b648d`: JavaFX 26 → 27.0.1 and the upstream fixes for its regressions.
- [x] Cherry-pick `999a823` without its lark pin change, `e840d47` and `2ce667d`: Jackson 2.21.1 → 2.21.8. Keep the 2.21.1 verification entries the locked lark revision needs.
- [x] Keep the drongo and lark pins and the bundled JDK (TASK-022) unchanged.

## Validation

The packaging check for the batch 1 versions failed before the cherry-picks and passes after them, with all 21 packaging checks. Strict dependency verification, 441 regression tests and 17 desktop tests pass. The verified Tor 409.5.0 libraries carry version 0.4.9.5 for Linux x86_64, macOS arm64 and Windows x64. The Linux app image bundles JavaFX 27.0.1+5 and only the Linux x86_64 Tor resources, and its launcher reports `PurityWallet 1.2.0`. Details are in the [migration report](../migration-report.md#batch-1-results--2026-11-28). The Windows MSI and Apple Silicon DMG are validated by PR CI.
