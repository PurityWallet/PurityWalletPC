# TASK-013: Fix Purity signet restart and network defaults

## Scope

- [x] Record REQ-012 and supersede the original mainnet/regtest-only restriction.
- [x] Accept signet at startup and use the same supported-network list in the restart menu.
- [x] Default signet to `ssl://electrum.bitcoinpurity.org:60002` and `https://mempool.bitcoinpurity.org/signet`.
- [x] Avoid duplicate `/signet` transaction paths; preserve custom connections, explorer templates and disabled exploration.
- [x] Pin the actual Purity signet activation and validate subsequent ASERT headers with its PoW limit and isolated cache, preserving verification and dependency sources.
- [x] Synchronize the product specification, architecture, API contract, migration report and task index.

## Validation

Regression tests first reproduced the missing signet default and the incompatible upstream checkpoint/retarget profile. Added fixtures contain the hash-linked median-time seed at 327011–327021 and 35 real headers at 327022–327056. Tests cover activation, changing difficulty, proof of work, median/future times, chain work, cache replay/rewind, supported networks, service defaults and transaction links.

Java 25 passed 387 regression and 7 desktop tests. All 13 Python packaging checks passed. Live signet acceptance verified 1,851 forward headers, cache replay and valid/invalid Merkle proofs. The macOS arm64 app built, passed version/signature checks and completed signet desktop initialization in an isolated temporary home without changing the temporary mainnet configuration. Detailed evidence and platform limits are recorded in the migration report.
