# TASK-016: Integrate the PWC Java Provider SDK

## Scope

- [x] Add REQ-014 and synchronize the specification, architecture and API contract.
- [x] Add the SDK dependency, JVM native-access flags, jlink native-library handling, dependency verification and license notices.
- [x] Implement the wallet backend, ids, state persistence and event forwarding with tests first.
- [x] Implement the pairing, permission, PSBT and message prompts and the Settings PWC page.
- [x] Start and stop the provider with the desktop application; report endpoint errors and unsupported platforms.
- [x] Exercise pairing, grants, a balance read and a rejected signature through a LOCAL client on regtest.
- [x] Pass the SDK download token to CI and verify native-library loading in packages.

## Behavior

See REQ-014. The SDK provides the protocol; this project maps open wallets to PWC wallets and accounts, asks the user to decide every prompt, and applies REQ-002 fee sources and REQ-011 replay checks to PWC signing and broadcasting. No real transaction is signed or broadcast during acceptance.

## Validation

Local runs used OpenJDK 25.0.5 on Linux x86_64 with the SDK downloaded from GitHub Packages; CI pins Temurin 25.0.2.

- `./gradlew :test --dependency-verification strict`: 434 tests passed (the 388 existing tests plus 46 new); one Intel-Mac-only test was skipped here.
- `xvfb-run ./gradlew :desktopTest`: 15 tests passed (7 existing, 8 for PWC prompts and the Settings page).
- `python3 src/test/packaging_test.py`: 16 checks passed.
- `./gradlew jpackageImage --dependency-verification strict`: the Linux image holds `lib/runtime/lib/libpwc_provider_jni.so` and none of the SDK's bundled native resources.

The integration test runs the real Provider and LOCAL endpoint with a regtest test wallet and a LOCAL client: pairing, HMAC authentication, `PERMISSION_REQUIRED` before a grant, a granted balance read, a rejected `psbt.sign` and reconnection after a restart. The desktop test drives the real prompt handler through pairing, a grant, an approved and a refused message signature, then revokes and unpairs from the Settings page; renders are in `build/qa/pwc-*.png`.

The packaged Linux application, started on regtest with a private data directory, opened `~/.pwc/run/puritywallet-regtest.sock` (mode 0600) and answered `local.hello` from the pwc-testkit Python client. A copy of the packaged launcher running the SDK demo listened without extracting a temporary library. Windows and Apple Silicon macOS packages are checked the same way in the CI workflow's jobs (REQ-015). No real transaction was signed or broadcast.

## SDK limitations found

- `AccountInfo.scriptType` has only P2WPKH and P2PKH, so multisig, nested SegWit, Taproot and silent-payment accounts cannot be exposed.
- `local.hello` reports `provider_id` as `pwc-provider` whatever id the endpoint was started with.
- No macos-x86_64 native library, and no way to ask whether the platform is supported other than catching the linkage error.
- No callback when a Client unpairs or revokes, or when a prompt is withdrawn; the wallet saves on its own decisions and on exit, and polls for withdrawal.
- Locked wallets fail with `WALLET_LOCKED` instead of `awaiting_unlock`, so the wallet cannot offer to unlock first (PWC-UX-001 §10).
- `Prompt` and `EndpointException` cannot be constructed outside the SDK, and where the library was loaded from is not visible.
- `docs/api-contract.md` §8.4 still shows `ALL-UNNAMED` and `-cp` launch commands.
