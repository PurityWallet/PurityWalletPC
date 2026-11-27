# TASK-019: Keep the PWC backend free of waits

## Scope

- [x] Add REQ-017, amend REQ-011 and REQ-014, and synchronize the specification, architecture, API contract and task index.
- [x] Write failing regression tests for the backend without waits, the replay clearance, device signatures in the prompt flow, broadcast timeouts and error mapping.
- [x] Publish an immutable wallet and connection view from the JavaFX thread and read only it in the backend.
- [x] Move device signing into the PSBT prompt flow; bind kept approvals to their request and expire them.
- [x] Record replay clearances in the signing prompt and require them for PWC broadcasts on mainnet and signet.
- [x] Bound the broadcast, map its failures and wrap every backend method.
- [ ] Upgrade the SDK to 0.2.1: the dependency, packaging check and comments are updated, but 0.2.1 is not yet published to GitHub Packages, so its verified checksums cannot be recorded.
- [ ] Run the regtest end-to-end flow with the pwc-spec Python client.

## Behavior

See REQ-017 and the "PWC backend without waits" section of the architecture.

## Validation

Local runs used Temurin 25.0.2 on Linux x86_64. SDK 0.2.1 exists only as the unpublished pwc-spec branch `claude/great-bell-vf3emq` (its Java API is unchanged from 0.2.0), so the tests ran with the published, verified 0.2.0 substituted by a session-local Gradle init script; dependency verification stayed strict.

- The new tests failed first: they did not compile against the old `PwcHost` and backend.
- `./gradlew :test` ran 442 tests with no failure (one Intel-Mac-only test skipped). `PwcWalletBackendTest` (24 tests) covers every backend method answering while the wallet thread never runs, the published view against later changes (including frozen coins), addresses never handed out twice from a stale view, failures of the view (including a JavaFX `IllegalStateException` and a `LinkageError`) becoming `INTERNAL_ERROR` or empty answers, the txid in display order, broadcast refusals, a 300 ms broadcast limit giving `NETWORK_UNAVAILABLE`, mainnet and signet broadcasts refused with `POLICY_VIOLATION` without a clearance and accepted once with one, kept decrypted wallets and device signatures bound to their PSBT, inputs or message, used once, expired and cleared. `AppPwcHostTest` covers the mapping of Electrum failures.
- `xvfb-run ./gradlew :desktopTest` ran 17 tests with no failure. `PwcDesktopTest` adds a real Provider and LOCAL clients in which a fake USB device signs in the PSBT prompt flow before the prompt is answered while it holds the JavaFX thread, a second paired application's `account.getBalance` is answered within 2 seconds meanwhile, and the finalized transaction is broadcast through `transaction.broadcast`; and `AppPwcHost` with a connection state that, like `AppServices.isConnected()`, throws off the JavaFX thread, where balance, UTXO, address, PSBT, signature and broadcast calls complete within 2 seconds while the JavaFX thread is blocked and the transaction reaches the server (the regression test for the signet `INTERNAL_ERROR`).
- `python3 src/test/packaging_test.py` fails one check only: the dependency verification entry of `pwc-provider-java` 0.2.1, which can be recorded only from the published artifact (`./gradlew --write-verification-metadata sha512 help`, then review the new entry). CI cannot resolve 0.2.1 until then.
- Not run: the end-to-end flow with the pwc-spec Python client. `pwc-client` checks the provider id of `local.hello`, which needs SDK 0.2.1 at run time, and the session had no Bitcoin Purity Core node for regtest or Purity signet.
