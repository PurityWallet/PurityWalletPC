# TASK-005: Purity mainnet verification adaptation

Status: Complete — locally verified, including full live mainnet sync

Requirements: REQ-009

## Work
Implement application-owned checkpoint/state selection, integer ASERT and activation pinning; integrate storage, proof, sync and reorg paths. Authenticate the eleven-header MTP seed and isolate mainnet header caches. Recheck all stored post-fork wallet proofs on first fetch. Keep dependency sources and security switches unchanged.

## Verification
TDD for activation, ASERT rounding/clamping, invalid headers, cached-file revalidation and existing non-mainnet behavior. Run complete regressions and full live synchronization from 961632 to an announced tip, reload/replay, a transaction Merkle proof and failure controls. Record commands, heights, hashes and remaining limitations in the migration report.

## Results
- TDD regression cases cover the common checkpoint, authenticated MTP seed, activation identity, ASERT/Core reference arithmetic, rejected difficulty/PoW/time claims, cache replay/corruption, old wallet proofs outside the recent reorg window and mandatory per-peer activation checks with a populated cache.
- Final local checks: 366 ordinary regression tests and 3 graphical desktop tests passed; macOS application image built successfully and includes the new validator/profile classes and authenticated anchor resource.
- Final read-only network acceptance: 961632–971156, 9525 consecutive headers, identical announced/verified tip, replayed hash/work and a real post-fork Merkle inclusion proof. Invalid proof controls were rejected. The shared historical checkpoint gate also passed separately.
- Both dependency source trees remain clean at their original pinned commits; verification switches remained enabled. See [migration results](../migration-report.md) for exact hashes and commands.
