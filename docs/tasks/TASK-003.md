# TASK-003: Set the Initial Release Version to 1.0.0b

Source: `input/requirements.md`.

- [x] Add version regression checks before changing the implementation.
- [x] Set application and archive versions to `1.0.0b`.
- [x] Use numeric `1.0.0` in native package metadata.
- [x] Preserve the beta suffix when locating release signatures.
- [x] Synchronize requirements, product specification, architecture, API contract and migration report.
- [x] Run regression checks and rebuild the macOS application and ZIP.

Upstream copyright and source-version attribution remain unchanged.

Validation: 352 Java regression tests and ten Python checks passed. The compiled download-verifier pattern preserves `1.0.0b` when matching release files. The macOS launcher reports `PurityWallet 1.0.0b`; the bundle uses numeric version `1.0.0`. The rebuilt `puritywallet-1.0.0b.zip` was checked against the app metadata and runtime image.
