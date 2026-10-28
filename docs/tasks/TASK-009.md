# TASK-009: Legacy-chain UTXO and replay warnings

Source: REQ-011 in `input/requirements.md`.

- [x] Align intake, product specification, architecture, API contract and task index.
- [x] Add failing tests for exact output matching, failures, mixed inputs and acknowledgement/cancellation.
- [x] Implement background per-UTXO and selected-input checks and signing/broadcast protection.
- [x] Verify regression tests, JavaFX interactions and visible warnings; record evidence in the migration report.

No dependency changes, real signing or transaction broadcasting are included in validation.

## Verification — 2026-10-28 (Australia/Melbourne)

- TDD: the new checker tests failed before implementation; a JavaFX assertion also reproduced truncated warning text before the layout fix.
- `JAVA_HOME=/private/tmp/puritywallet-jdk25/Contents/Home ./gradlew :test :desktopTest --console=plain`: 378 regression tests and 7 desktop tests passed, with no failures or skipped tests.
- `:verifyCheckpoint --tests '*LegacyUtxoCheckerTest.liveLegacyApiRecognizesMissingTransaction'`: passed a read-only request using a public synthetic txid; the expected transaction HTTP 404 is correctly classified as absent. Full matching/output/spent/failure behavior is covered by deterministic fixtures.
- Desktop checks cover visible per-row states, complete warning text in light/dark themes and the actual Send layout, explicit acceptance/cancellation, stale results and cancellation at software/device signing, seed signing and broadcast entry points. QR seed cancellation clears the temporary seed.
- Preview files: `build/qa/legacy-replay-light.png`, `build/qa/legacy-replay-dark.png`, `build/qa/send-legacy-replay.png`.
- `git diff --check` passed. Pinned drongo and lark source trees are unchanged. No real payment, physical hardware signing, deployment or release publication was performed. Live Tor/proxy routing and native Windows/Linux UI execution were not exercised.
