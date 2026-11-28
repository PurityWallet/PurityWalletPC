# PurityWallet Product Specification

## Identity and artwork

Full and short name: **PurityWallet**. Command: `puritywallet`.
Release version: **1.2.0**, shared by the application, archives, native installers and bundles.
Website: https://bitcoininstitute.com.au.

Retain internal Java package names and compatible third-party import identifiers.
Use the existing blue-and-white PurityWallet logo from the local mobile project, scaled to the required sizes, for application icons, system tray icons, welcome and about screens, dialogs, notifications and PurityWallet wallet-model views.
Keep other wallet vendors' artwork and hardware-wallet menu names unchanged.

## Bitcoin Purity mainnet

Default Electrum: `ssl://electrum.bitcoinpurity.org:50002`.
Explorer and fallback broadcast: https://mempool.bitcoinpurity.org.
Preserve shared addresses, PSBT, `bitcoin:` payment URIs and amount encoding.

Activation height: **961637**.
Pinned activation hash: `0000000000000000003ea74f4dafdda7ed4e02c4c1ccb9768e0ca4f9e1a35159`.
ASERT anchor height: **961632**; target block spacing: **600 seconds**; half-life: **86400 seconds**.

Keep Bitcoin-only checkpoints and services out of the Purity mainnet path, and do not display BTC market prices as Purity prices.
Purity signet is supported with its own fork profile and services. Bitcoin testnet3/testnet4 cannot start as Purity networks. Keep regtest for development.

## Distribution and licensing

Desktop and headless editions both use the PurityWallet product name and the `puritywallet` launcher.
Retain the upstream Apache 2.0 LICENSE and copyright notices. Distributions include origin and modification notices.
Use multi-resolution PurityWallet icons in macOS ICNS, Windows ICO and Linux PNG resources.
Bundle Eclipse Temurin 25.0.5.1+2 in PurityWallet distributions. Pin release builds and CI to that exact JDK.

## Documentation

All project documentation, including requirements, architecture, API contracts, migration reports and task records, is written in English.

Release 1.2.0 includes a Windows x64 MSI and a macOS Apple Silicon DMG, each built on its target platform and supplied with a SHA-256 checksum. Installer filenames and metadata use version `1.2.0`.

macOS CI builds an Apple Silicon (`arm64`) DMG alongside Windows MSI validation on PRs targeting master, pushes to master and manual dispatch. Each DMG contains `puritywallet.app` and an Applications shortcut. Before uploading the DMG and SHA-256 as Actions artifacts, CI mounts the image and verifies the application's version, architecture, bundle identity and ad hoc signature. These builds do not require Apple credentials and are not notarized.

Signing private keys, certificates containing private keys, passwords and notarization credentials must remain outside GitHub repository contents, Secrets, logs and artifacts. Official signing and notarization run locally. Both automatic and manually dispatched GitHub packaging workflows use credential-free ad hoc macOS signing only.

Push the CI configuration to master to trigger installer generation. Official signing and notarization are deferred until the Apple Developer account's organization conversion is complete; keys and passwords remain on the local machine.

# Product specification

## REQ-010: Automatic PR validation — adopted
GitHub Actions validates opened, reopened and updated PRs targeting master and pushes to master with one CI workflow (REQ-015). Its tests job runs the pinned Java 25 regression suite and desktop tests under Xvfb with isolated test homes. Its Windows job runs regression and packaging checks, builds an unsigned MSI, installs it and verifies the installed version. The workflow supports manual dispatch, use read-only repository permissions and retain test reports. CI acceptance requires successful runs for the current PR revision; a rejected dispatch or an empty checks list is not a pass. No release publication or dependency-source changes are included.

## REQ-001: PurityWallet brand — adopted
Use PurityWallet for visible application, CLI and package names. Derive desktop icons from the mobile blue master. Preserve Java packages, dependency coordinates, application identities, storage paths, wallet formats, upstream attribution and licenses. Help, issues and releases use https://github.com/PurityWallet/PurityWalletPC. Disable upstream automatic version checks and hide their switch; offer manual releases access.

## REQ-002: Purity defaults — adopted
Bundle only Purity public Electrum endpoints: ssl://electrum.bitcoinpurity.org:50002 (mainnet) and ssl://electrum.bitcoinpurity.org:60002 (signet). The predefined explorer is https://mempool.bitcoinpurity.org on mainnet and https://mempool.bitcoinpurity.org/signet on signet; custom explorers and disabling exploration remain available. Preserve explicit custom Electrum/Core connections. Never retry legacy bundled public peers. Use connected-node fee estimates and retain manual fees; do not fetch Bitcoin third-party fees. Hide fiat settings and valuations and stop exchange requests even with saved configurations. Support mainnet, Purity signet and development regtest; unsupported Bitcoin testnets must not appear in the restart menu.

## REQ-003: In-wallet donations — adopted
Donate in About selects an eligible open wallet, including hardware, multisig and watch-only/PSBT workflows. Always ask, even with one wallet. One replaceable recipient constant initially contains an invalid placeholder; display an unavailable message until valid. A configured recipient opens the normal Send view with no amount. Cancellation or no eligible wallet makes no payment. Never automatically construct, sign or broadcast a transaction.

## REQ-004: Hide Lightning creation — already satisfied
There are no Lightning, LNDHub or Ark wallet creation entries. Preserve LNURL-auth and lightning: handling.

## REQ-005: Android identity — not adopted
Android-only. Preserve the base release independent desktop identity and storage. No automatic data migration is performed.

## REQ-006: Simplified creation — not adopted
Keep full desktop single-signature, multisig, script-type and hardware configuration. Keep native SegWit single-signature as the existing default. Preserve imports and existing wallets.

## REQ-007: iOS identity — not adopted
Do not introduce iOS extensions/App Groups. Preserve org.bitcoinpurity.puritywallet as the base release macOS Bundle ID.

## REQ-008: Mobile push service — not adopted
Keep existing desktop transaction notifications and their switch, updating visible brand only. Do not register mobile push tokens or connect a remote push backend.

## Acceptance criteria
Fresh and reset defaults offer only Purity services. Explicit custom connections survive configuration reload. Saved external fee, exchange and update preferences cannot activate their services. Manual fees and desktop notification preferences remain available. Rebranding must leave default/XDG/custom storage locations and serialized wallet formats unchanged. Icons and names cover Windows, macOS and Linux. Donation always requires selection and the existing payment review/signing workflow; the shipped placeholder cannot enter payment creation.

## REQ-009: Purity mainnet header verification — adopted
Adapt mainnet validation in this application without changing dependency sources or disabling verification. Use the common checkpoint at 961631 and the consensus-pinned Purity activation block at 961637. Validate the pre-activation headers against the shared target and subsequent headers against Purity Core ASERT (anchor 961632, anchor-parent time at 961631, spacing 600 seconds, half-life 86400 seconds). Preserve Core's activation-only harder-target allowance alongside the exact activation hash. Enforce linkage, claimed PoW/powLimit, seeded eleven-header MTP, future-time bound, Merkle inclusion and cumulative-work reorg checks. Historical proof anchors must come from the same application profile. On first wallet fetch, recheck every stored proof above 961631, including records older than the recent reorg window. Isolate only the mainnet header cache by chain/version; preserve wallet paths and identities. Regtest and unsupported Bitcoin testnet validation delegate to the unchanged dependency; Purity signet uses the application-owned profile in REQ-012. Complete offline regression, full live header synchronization/replay and transaction-proof acceptance before removing the recorded blocker.

## REQ-011: Legacy-chain UTXO and replay warnings
On mainnet and Purity signet, check every UTXO against the corresponding legacy Bitcoin network: mainnet uses https://mempool.space/api and signet uses https://mempool.space/signet/api. Never send signet checks to the mainnet API. Mark an output as shared only when txid, output index, amount and script match an unspent legacy output. Show a prominent warning for each shared output and distinguish checking and unavailable results. On Send, check the actual transaction inputs after coin selection (including automatic selection). Warn clearly when all inputs are shared. Require explicit acknowledgement of replay risk before signing or broadcasting; cancellation must stop the action. An unavailable or malformed check must remain unknown and require an explicit risk acknowledgement to continue. Recheck before protected actions, discard stale results after input/transaction changes, respect offline mode and the configured proxy, and keep regtest and unsupported testnets free of legacy requests. Use the mobile wallet's read-only mempool.space API approach without changing dependency sources or wallet formats.

## REQ-012: Purity signet switching and defaults

Switching to signet must restart successfully rather than exit at the network restriction. Use the signet TLS Electrum and explorer defaults from REQ-002, with exactly one /signet segment in transaction links. Keep network-specific configuration and wallets separate, preserve custom connections/explorers and disabled exploration, and retain mainnet defaults. Authenticate Purity signet's activation block at 327022, validate subsequent headers using its ASERT parameters (anchor 327017, spacing 600 seconds, half-life 86400 seconds), and isolate its header cache from the original Bitcoin signet profile. Do not modify dependency sources or disable transaction verification.

## REQ-013: Versioned signet comparison build

Use 1.1.1 consistently in application, archives and native bundle/installer metadata to distinguish the signet comparison build. Preserve behavior, dependencies and signing policy. Compare normal Chrome download behavior using the recorded DMG checksum. A changed hash does not establish Google review approval or a cleared verdict.

## REQ-014: PWC LOCAL provider — adopted

PurityWallet desktop is a PWC LOCAL Provider (Core v0.2.1 over LOCAL v0.1.0) built on the PWC Java Provider SDK 0.2.1 (REQ-017). Applications on the same computer connect through a Unix socket (macOS, Linux) or named pipe (Windows), pair, request permissions, read balances, UTXOs, addresses and transactions, create PSBTs, and request PSBT and message signatures. Every decision is taken by the user in the wallet.

| Item | Specification |
|---|---|
| Switch | Settings → PWC; enabled by default; desktop mode only |
| Provider id | `puritywallet` (mainnet), `puritywallet-signet`, `puritywallet-regtest` |
| Endpoint | The SDK default endpoint directory (`$XDG_RUNTIME_DIR/pwc`, `~/.pwc/run`, `~/Library/Application Support/PWC/run`) or the per-user named pipe |
| Wallets | Open wallets only; one PWC wallet per wallet file; the master wallet and each non-nested child wallet are accounts |
| Ids | HMAC-SHA256 of the account output descriptor (wallet: of the master descriptor) under a random salt stored in `<data dir>/pwc/id-salt`; first 16 bytes in lower-case hex |
| States | Ready; syncing while history loads; locked behind Lock Wallet. Unopened wallets are not listed |
| Signing accounts | Software single-sig P2WPKH: PSBT and `bip322-simple`; software single-sig P2PKH: `legacy` messages; USB hardware single-sig P2WPKH: PSBT. Other single-sig P2WPKH and P2PKH accounts are read-only; multisig, nested SegWit, Taproot and silent-payment accounts are not exposed (the SDK represents P2WPKH and P2PKH only) |
| Passwords | Asked after approval, in a separate dialog; one decrypted copy per signature, bound to that request, valid 2 minutes, cleared afterwards (REQ-017) |
| Fees and broadcast | Connected-node estimates only; broadcast through the current connection within 30 seconds, `NETWORK_UNAVAILABLE` when offline, unreachable or late (REQ-017) |
| Replay protection | REQ-011 check and acknowledgement before approving a PWC signature; the clearance covers the PWC broadcast of that transaction for 30 minutes; other PWC broadcasts on mainnet and signet get `POLICY_VIOLATION` (REQ-017) |
| Access lifetime | Pairings and grants expire when PWC stops. Applications pair and request access again after a wallet restart or when PWC is turned off and on. Wallet and account IDs remain stable. |
| Platforms | linux-x86_64, linux-aarch64, macos-aarch64, windows-x86_64. Intel Macs show PWC as unavailable; CI builds no Intel DMG |

Prompts follow PWC-UX-001: pairing shows the peer process and the six-digit code, with the claimed name marked unverified; permission prompts let the user narrow scopes, accounts, expiry and amount limits; PSBT prompts show the SDK-computed outputs, fee, fee rate, total leaving the account and change; message prompts show the exact message, address and format. Withdrawn prompts close automatically and closing a prompt rejects it. Endpoint errors (already running, name taken, unsafe directory, path too long, I/O) are reported in the PWC page without affecting the rest of the wallet.

## REQ-015: Single CI workflow — adopted

`.github/workflows/ci.yaml` (workflow name `CI`) holds every PR check as three jobs, so one build of a CI service that runs one workflow at a time covers all of them:

| Job | Runner | Checks |
|---|---|---|
| `tests` | `ubuntu-22.04` | Packaging checks, regression and desktop tests under Xvfb |
| `windows-installer` | `windows-2022` | Regression tests, MSI build, silent installation, installed version and PWC native library |
| `macos-dmg` | `macos-15` | Regression tests, Apple Silicon app and DMG, mounted-image checks, ad hoc signature and PWC native library |

Triggers, permissions, the `PWC_PACKAGES_TOKEN` step-level secret and artifact names are unchanged. No step reads a GitHub workflow token, so the jobs run on self-hosted Buildkite agents, which receive none; `actions/setup-java` is given an empty `token` because its default reads `github.token`. CI no longer builds an Intel DMG; the manual Package workflow still can.

## REQ-016: Test coverage reports in CI — adopted

Every job of `ci.yaml` runs `:jacocoTestReport` after `:test`. The report covers the main source set under the `:test` suite only; `desktopTest` and `verifyCheckpoint` run without the JaCoCo agent. The interface-only and hardware card driver packages `control`, `glyphfont`, `keystoreimport`, `settings`, `terminal`, `transaction`, `io/ckcard`, `io/keycard` and `io/satochip` are excluded from the report; mixed packages such as `wallet`, `pwc` and the root package are included. Each job prints the line and branch coverage in its log and uploads the HTML and XML report as an artifact:

| Job | Artifact |
|---|---|
| `tests` | `PurityWallet-linux-coverage` |
| `windows-installer` | `PurityWallet-windows-coverage` |
| `macos-dmg` | `PurityWallet-macos-arm64-coverage` |

Coverage never fails a build and is not uploaded to an external service.

## REQ-017: PWC backend never waits — adopted

PWC Java Provider SDK 0.2.1 calls the wallet backend without its state lock, so a slow backend method delays every other Core request of every paired application. The wallet backend therefore never waits for the user or the JavaFX thread, and every method has a bounded duration.

| Item | Specification |
|---|---|
| SDK | `org.bitcoinpurity.pwc:pwc-provider-java:0.2.1`; `local.hello` reports the REQ-014 provider id |
| Wallet data | Read from an immutable view of the open wallets and the connection, republished by the JavaFX thread after wallet and connection events; no backend method touches JavaFX objects or live wallet models |
| New addresses | Chosen from the view, never handed out twice in a session; the label and gap limit are applied to the wallet on the JavaFX thread without waiting |
| User interaction | Only in the prompt flow, after the user approves and before the prompt is answered: replay acknowledgement, password, device confirmation |
| Hardware signing | The device dialog runs in the PSBT prompt flow; `signPsbt` uses the verified device signatures for that exact PSBT, account and inputs; cancelling, unplugging or a withdrawn prompt rejects the request |
| Kept approvals | A decrypted wallet or device signature is bound to the account and the exact PSBT and inputs (or message, address and format), used once and valid 2 minutes; decrypted keys are cleared after use or expiry |
| Replay clearance | Recorded in the PSBT prompt when the check was absent or the user acknowledged a shared or unknown result; matched to the broadcast by version, lock time, inputs with sequences and outputs; valid 30 minutes and consumed by a successful broadcast |
| Uncleared broadcast | On mainnet and Purity signet, `POLICY_VIOLATION` without a dialog or network request; the user can broadcast from the wallet UI under REQ-011. Regtest needs no clearance |
| Broadcast limit | 30 seconds; timeout or unreachable server `NETWORK_UNAVAILABLE`; server refusal `TRANSACTION_REJECTED`; the txid is returned in display order |
| Errors | Backend methods return valid results or throw `PwcException`; unexpected failures become `INTERNAL_ERROR` (or an empty answer for methods without errors) and are logged |
| Host calls | Pairings, grants, revocation, unpairing and state saving stay on the JavaFX thread |

Acceptance: regression tests first, then an end-to-end regtest run with test wallets and the pwc-spec Python client (`pwc-client-ffi`, `try_spend.py` flow): a self-transfer reaches the mempool, and a second application's `account.getBalance` is answered within 2 seconds while the first application's signing prompt is open.
