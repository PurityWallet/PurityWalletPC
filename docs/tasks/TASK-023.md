# TASK-023: Bring in upstream Sparrow batch 1 (bundled runtime libraries)

REQ-018 batch 1. The analysis of all upstream changes is in the [migration report](../migration-report.md#upstream-sparrow-comparison--2026-11-28-req-018).

## Scope

- [ ] Add packaging checks for the batch 1 library versions and the macOS texture size option.
- [ ] Cherry-pick `21b4032`: Tor 0.4.8.21 → 0.4.9.5 (`io.matthewnelson.kmp-tor` 409.5.0).
- [ ] Cherry-pick `8ea8e30`, `f13a4d9`, `114389b` and `d0b648d`: JavaFX 26 → 27.0.1 and the upstream fixes for its regressions.
- [ ] Cherry-pick `999a823` without its lark pin change, `e840d47` and `2ce667d`: Jackson 2.21.1 → 2.21.8. Keep the 2.21.1 verification entries the locked lark revision needs.
- [ ] Keep the drongo and lark pins and the bundled JDK (TASK-022) unchanged.

## Validation
