# TASK-015: Enable signet legacy-chain detection and replay warnings

## Scope

- [x] Extend REQ-011 to Purity signet and synchronize the specification, architecture and API contract.
- [x] Reproduce missing signet guard and warning behavior with desktop regressions before implementation.
- [x] Select the corresponding legacy network API and capture it before background execution.
- [x] Enable signet UTXO states/recheck, Send input checks and signing/broadcast risk acknowledgement.
- [x] Validate regression, desktop and live API checks.
- [x] Rebuild and verify the local arm64 DMG with the signet legacy checks included.

## Behavior

Mainnet uses `https://mempool.space/api`; signet uses `https://mempool.space/signet/api`, without falling back to mainnet. Exact output matching, transient results, proxy/offline handling, explicit risk acknowledgement, cancellation and stale-result checks retain the mainnet semantics. Regtest and unsupported testnets remain excluded. Dependency sources and wallet formats are unchanged. No transactions are signed or broadcast during acceptance.

## Validation

Temurin 25.0.2+10 passed 388 Java regression tests, all 7 desktop tests and 13 Python packaging checks. Existing desktop replay tests now exercise mainnet and signet: shared/unknown/absent decisions, explicit acceptance/cancellation, stale snapshots, offline results, regtest bypass, UTXO recheck/column rendering, Send preview and software/broadcast/QR-seed entry points. Both real legacy APIs returned HTTP 404 for a synthetic missing txid; the tagged Java live check passed using each network-specific endpoint. No real wallet identifiers or transactions were sent.

Signet UI evidence is under `build/qa/legacy-replay-light-signet.png`, `legacy-replay-dark-signet.png` and `send-legacy-replay-signet.png`. The visible status column and complete Send warning were inspected. No dependency sources were modified; no commit, push or hosted CI acceptance is claimed.

## Current local package

`build/macos-release/puritywallet-1.1.1-signet-legacy-macos-arm64.dmg` contains this change, unlike the earlier version-only DMG in TASK-014. SHA-256: `35317568df8093da2b2ba2a8dc5d4f9be03f147838e8cd49481d25efa06bc93d`. The package build succeeded; its embedded legacy-checker bytecode matches the tested class, including signet API selection. The DMG passed image checksum verification and read-only mounted checks for the Applications shortcut, arm64 architecture, both 1.1.1 bundle version fields, CLI version and deep/strict ad hoc signature. It was detached afterwards. No Intel package, hosted CI run, release publication or Chrome download verdict is claimed.
