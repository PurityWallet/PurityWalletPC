# PurityWallet Desktop Requirements

- Both the full and short product names are PurityWallet, derived from Sparrow Wallet.
- Use Bitcoin Purity mainnet, taking chain and service parameters from the local BitcoinPurity and PurityWallet projects.
- Website: https://bitcoininstitute.com.au (including the GitHub repository About link).
- Command and executable names: puritywallet; puritywallet.exe and puritywallet.bat on Windows.
- Use independent PurityWallet configuration, wallets, logs and instance identifiers.
- As confirmed by the user, retain the actual upstream Apache 2.0 license and copyright notices rather than changing to GPLv2.
- Change only this project. Do not modify dependency sources, including drongo and lark.
- Replace application icons and Sparrow artwork throughout the UI with the existing brand assets from the local PurityWallet project.
- Write all project documentation in English.
- The PurityWallet release version is 1.1.1, including application, archive and native installer metadata. Show a single version in the README.
- Provide a Windows x64 MSI installer for release 1.1.1, built from the current PurityWallet sources on Windows.
- Build macOS Apple Silicon DMGs in CI alongside Windows MSI validation for PRs targeting master, pushes to master and manual dispatch. Upload versioned DMGs and SHA-256 files as Actions artifacts. Verify the mounted app's version, architecture, bundle metadata and ad hoc signature without Apple credentials or notarization.
- Signing private keys, certificates containing private keys, passwords and notarization credentials must never enter GitHub, including repository contents, Secrets, logs and artifacts. Official signing and notarization must run locally; all GitHub packaging workflows must work without those credentials.
- Commit and push the CI changes to master to trigger Windows MSI and macOS DMG builds. Defer official signing and notarization until the Apple Developer account's organization conversion is complete, keeping keys and passwords on the local machine.

# Mobile requirement adaptation

## REQ-010: Automatic PR validation — adopted
Run Java 25 regression and desktop tests and Windows MSI build/install/version validation when a PR targeting master is opened, reopened or updated. Validate pushes to master and retain manual workflow dispatch. Use pinned actions and dependencies, read-only repository permissions, isolated test data and unsigned CI artifacts. First attempt manual validation of PR #2 at 03e2a05b; repair observed failures and require successful GitHub runs before claiming CI acceptance. Do not modify dependency sources or publish releases.

## REQ-001: PurityWallet brand — adopted
Use PurityWallet for visible application, CLI and package names. Derive desktop icons from the mobile blue master. Preserve Java packages, dependency coordinates, application identities, storage paths, wallet formats, upstream attribution and licenses. Help, issues and releases use https://github.com/PurityWallet/PurityWalletPC. Disable upstream automatic version checks and hide their switch; offer manual releases access.

## REQ-002: Purity defaults — adopted
Bundle only Purity public Electrum endpoints: ssl://electrum.bitcoinpurity.org:50002 (mainnet) and ssl://electrum.bitcoinpurity.org:60002 (signet). The predefined explorer is https://mempool.bitcoinpurity.org on mainnet and https://mempool.bitcoinpurity.org/signet on signet; custom explorers and disabling exploration remain available. Preserve explicit custom Electrum/Core connections. Never retry legacy bundled public peers. Use connected-node fee estimates and retain manual fees; do not fetch Bitcoin third-party fees. Hide fiat settings and valuations and stop exchange requests even with saved configurations. Support mainnet, Purity signet and development regtest; unsupported Bitcoin testnets must not appear in the restart menu.

## REQ-012: Purity signet switching and defaults
Switching to signet must restart successfully rather than exit at the network restriction. Use the signet TLS Electrum and explorer defaults from REQ-002, with exactly one /signet segment in transaction links. Keep network-specific configuration and wallets separate, preserve custom connections/explorers and disabled exploration, and retain mainnet defaults. Authenticate Purity signet's activation block at 327022, validate subsequent headers using its ASERT parameters (anchor 327017, spacing 600 seconds, half-life 86400 seconds), and isolate its header cache from the original Bitcoin signet profile. Do not modify dependency sources or disable transaction verification.

## REQ-003: In-wallet donations — adopted
Donate in About selects an eligible open wallet, including hardware, multisig and watch-only/PSBT workflows. Always ask, even with one wallet. One replaceable recipient constant initially contains an invalid placeholder; display an unavailable message until valid. A configured recipient opens the normal Send view with no amount. Cancellation or no eligible wallet makes no payment. Never automatically construct, sign or broadcast a transaction.

## REQ-004: Hide Lightning creation — already satisfied
There are no Lightning, LNDHub or Ark wallet creation entries. Preserve LNURL-auth and lightning: handling.

## REQ-005: Android identity — not adopted
Android-only. Do not introduce Android-specific identifiers. Preserve the independent desktop identity and storage already established by the base release; no automatic data migration is performed.

## REQ-006: Simplified creation — not adopted
Keep full desktop single-signature, multisig, script-type and hardware configuration. Keep native SegWit single-signature as the existing default. Preserve imports and existing wallets.

## REQ-007: iOS identity — not adopted
Do not introduce iOS extensions/App Groups. Preserve the base release macOS Bundle ID org.bitcoinpurity.puritywallet.

## REQ-008: Mobile push service — not adopted
Keep existing desktop transaction notifications and their switch, updating visible brand only. Do not register mobile push tokens or connect a remote push backend.

## REQ-009: Purity mainnet header verification — adopted
Adapt mainnet validation in this application without changing dependency sources or disabling verification. Use the common checkpoint at 961631 and the consensus-pinned Purity activation block at 961637. Validate the pre-activation headers against the shared target and subsequent headers against Purity Core ASERT (anchor 961632, anchor-parent time at 961631, spacing 600 seconds, half-life 86400 seconds). Preserve Core's activation-only harder-target allowance alongside the exact activation hash. Enforce linkage, claimed PoW/powLimit, seeded eleven-header MTP, future-time bound, Merkle inclusion and cumulative-work reorg checks. Historical proof anchors must come from the same application profile. On first wallet fetch, recheck every stored proof above 961631, including records older than the recent reorg window. Isolate only the mainnet header cache by chain/version; preserve wallet paths and identities. Regtest and unsupported Bitcoin testnet validation delegate to the unchanged dependency; Purity signet uses the application-owned profile in REQ-012. Complete offline regression, full live header synchronization/replay and transaction-proof acceptance before removing the recorded blocker.

## REQ-011: Legacy-chain UTXO and replay warnings
On mainnet and Purity signet, check every UTXO against the corresponding legacy Bitcoin network: mainnet uses https://mempool.space/api and signet uses https://mempool.space/signet/api. Never send signet checks to the mainnet API. Mark an output as shared only when txid, output index, amount and script match an unspent legacy output. Show a prominent warning for each shared output and distinguish checking and unavailable results. On Send, check the actual transaction inputs after coin selection (including automatic selection). Warn clearly when all inputs are shared. Require explicit acknowledgement of replay risk before signing or broadcasting; cancellation must stop the action. For PWC requests the acknowledgement is taken in the signing prompt and covers the later PWC broadcast of that transaction (REQ-017). An unavailable or malformed check must remain unknown and require an explicit risk acknowledgement to continue. Recheck before protected actions, discard stale results after input/transaction changes, respect offline mode and the configured proxy, and keep regtest and unsupported testnets free of legacy requests. Use the mobile wallet's read-only mempool.space API approach without changing dependency sources or wallet formats.

## REQ-013: Versioned signet comparison build
Produce a distinguishable 1.1.1 build of the signet fix by changing only application and package version metadata. Preserve wallet behavior, network validation, dependencies and signing policy. Record the new DMG checksum for a normal Chrome download comparison; a different binary hash is not evidence of Google review approval or a cleared download verdict.

## REQ-014: PWC LOCAL provider — adopted
Make the desktop edition a Purity Wallet Connect (PWC) Provider over the LOCAL transport using the PWC Java Provider SDK `org.bitcoinpurity.pwc:pwc-provider-java` (0.2.1 since REQ-017) (MIT, from GitHub Packages). The SDK's Rust Provider owns the protocol, pairing, authentication, authorization, rate limits and Operation state; this project supplies the wallet backend, the approval prompts, lifecycle, settings, packaging and CI. Do not modify the SDK, pwc-spec, drongo or lark.
- The service is enabled by default and runs in desktop mode only. A PWC page in Settings holds the switch, the endpoint status, paired applications and their grants, with revoke and unpair actions.
- Provider ids: `puritywallet` on mainnet, `puritywallet-signet` on signet and `puritywallet-regtest` on regtest, so instances of different networks can run at the same time.
- Only open wallets are exposed. A wallet file is one PWC wallet; the master wallet and each non-nested child wallet are its accounts. Wallet and account ids are an HMAC-SHA256 of the wallet's output descriptor under a random per-installation salt, so they survive restarts and renames without a mapping file and reveal no wallet data.
- Wallet state: open and unlocked is ready, or syncing while history loads; a wallet behind Lock Wallet is locked and its account data cannot be read.
- Signing: software single-signature P2WPKH accounts sign PSBTs and BIP-322 simple messages; software single-signature P2PKH accounts sign legacy messages only; USB hardware single-signature P2WPKH accounts sign PSBTs through the existing device dialog. Other single-signature P2WPKH and P2PKH accounts (watch-only, airgapped) are read-only. The SDK represents P2WPKH and P2PKH accounts only, so multisignature, nested SegWit, Taproot and silent-payment accounts are not exposed. An encrypted wallet asks for its password in a separate dialog after the user approves; the decrypted copy is used for that one signature and then cleared.
- Prompts follow PWC-UX-001: the application's claimed name is marked unverified; signing prompts show only what the wallet computed from the transaction; approval is never the default button.
- Fee estimates come only from the connected node (REQ-002). Broadcasting uses the current Electrum or Core connection.
- REQ-011 applies to PWC: the legacy-chain check and the replay acknowledgement run before a PWC signature is approved, and cancellation rejects the request. A PWC broadcast uses the result cleared at signing (REQ-017).
- Pairings and grants live only in the running PWC service. When the wallet restarts or PWC is turned off and on, applications must pair and request access again. Keep the owner-only account ID salt under the network data directory so wallet and account IDs remain stable.
- The SDK ships native libraries for linux-x86_64, linux-aarch64, macos-aarch64 and windows-x86_64. On Intel Macs PWC is unavailable and reported as such; the rest of the application is unaffected. CI builds no Intel DMG (REQ-015). Packages load the native library from the runtime `lib` directory, not from a temporary copy.
- CI downloads the SDK with the read-only repository secret `PWC_PACKAGES_TOKEN`; it is not a signing or notarization credential. Dependency verification records the published checksums without relaxing the verification policy.
- Acceptance uses regtest and test wallets only; no real transaction is signed or broadcast.

## REQ-015: Single CI workflow — adopted
Run the PR checks as three jobs of one workflow, `.github/workflows/ci.yaml`: Linux regression and desktop tests, the Windows MSI build, installation and checks, and the macOS Apple Silicon DMG build and checks. CI services that run one workflow per build, such as Buildkite running GitHub Actions workflows, then cover every check in one build. Keep the existing triggers, steps, permissions, secret policy and artifacts. CI must run on self-hosted agents without a GitHub workflow token: the repository is public, so no step may read `github.token`, including through action input defaults. Stop building the Intel macOS DMG in CI; the manually dispatched Package workflow is unchanged.

## REQ-016: Test coverage reports in CI — adopted
Measure the code coverage of the `:test` regression suite with Gradle's JaCoCo plugin in every job of `.github/workflows/ci.yaml` (Linux, Windows and macOS). Each job prints the line and branch coverage in its log and uploads the HTML and XML reports as an artifact. Coverage is reported only: it does not fail a build, and no coverage data is sent to an external service. Desktop tests and the checkpoint task are not measured. The report leaves out the packages that hold only JavaFX or terminal interface code or hardware card drivers, which have no unit tests: `control`, `glyphfont`, `keystoreimport`, `settings`, `terminal`, `transaction`, `io/ckcard`, `io/keycard` and `io/satochip` under `com.sparrowwallet.sparrow`. Packages that mix interface and logic classes stay in the report. JaCoCo artifacts are added to dependency verification with signatures and checksums; the verification policy is unchanged.

## REQ-017: PWC backend never waits — adopted
Upgrade to the PWC Java Provider SDK `org.bitcoinpurity.pwc:pwc-provider-java:0.2.1`, whose `local.hello` reports the endpoint's provider id and whose Provider calls the `WalletBackend` without its state lock. Every backend method is then on the critical path of every connected application: while one runs, the other Core requests of all paired applications wait for it. Keep the REQ-014 provider ids. Change only this project.
- No `WalletBackend` method waits for the user (dialogs, device confirmation, password entry) or for the JavaFX application thread, directly or indirectly. User interaction tied to a PWC request happens in the prompt flow before the prompt is answered, or in the wallet's own UI outside any backend call.
- Backend methods never touch JavaFX objects (`Service`, `Task`, properties, controls) or wallet state that the JavaFX thread changes. They read an immutable view of the open wallets and of the connection published by the JavaFX thread. This fixes the Purity signet `transaction.broadcast` failure where `AppServices.isConnected()` read a JavaFX `Service` on a Provider thread and its `IllegalStateException` reached the SDK as `INTERNAL_ERROR`.
- Every backend method has a bounded worst-case duration. Network calls use explicit timeouts; a timeout or an unreachable server is a `PwcException` (`NETWORK_UNAVAILABLE` for a broadcast).
- A backend method returns a valid result or throws `PwcException`; no other exception reaches the SDK. `broadcast` returns the txid in display order.
- REQ-011 stays in force for PWC: no PWC broadcast of a transaction with inputs shared with the legacy chain, or with an unknown check result, without the user's explicit acknowledgement, and cancellation rejects the request. The check and acknowledgement happen in the PWC signing prompt, not inside `broadcast`.
- USB hardware accounts sign through the existing device dialog in the prompt flow; `signPsbt` only uses the signature the device returned.
- A PWC signature is bound to exactly what the user approved: the same PSBT, account and inputs. Anything kept between the approval and the backend call (a replay clearance, a device signature, a decrypted wallet) serves that one request, expires, and secrets are cleared after use.
- Acceptance: regression tests cover backend methods returning while the JavaFX thread is blocked, broadcasts with shared, unknown or unacknowledged inputs never opening a dialog and returning promptly, acknowledged transactions being broadcast, a fake device signing before the prompt is answered, network timeouts and their codes, no non-`PwcException` escaping, and `broadcast` off the JavaFX thread while connected. End to end on regtest with test wallets only and the pwc-spec Python client: a self-transfer is paired, read, created, signed, finalized and broadcast into the mempool, and a second application's `account.getBalance` is answered within 2 seconds while the first application's signing prompt, device confirmation or replay dialog is open.

This amends REQ-011 and REQ-014: for PWC, the legacy-chain check and replay acknowledgement run once, in the signing prompt, and their result is carried to the broadcast of that transaction; a PWC broadcast of a transaction this wallet never cleared through a PWC signing prompt on mainnet or Purity signet is refused with `POLICY_VIOLATION` and can be broadcast from the wallet's own UI instead, where REQ-011 applies unchanged.
