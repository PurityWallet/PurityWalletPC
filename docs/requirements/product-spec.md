# PurityWallet Product Specification

## Identity and artwork

Full and short name: **PurityWallet**. Command: `puritywallet`.
Release version: **1.1.0**, shared by the application, archives, native installers and bundles.
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
Bitcoin test networks without Purity parameters cannot start as Purity networks. Keep regtest for development.

## Distribution and licensing

Desktop and headless editions both use the PurityWallet product name and the `puritywallet` launcher.
Retain the upstream Apache 2.0 LICENSE and copyright notices. Distributions include origin and modification notices.
Use multi-resolution PurityWallet icons in macOS ICNS, Windows ICO and Linux PNG resources.

## Documentation

All project documentation, including requirements, architecture, API contracts, migration reports and task records, is written in English.

Windows x64 releases include an MSI installer built on Windows. The installer filename uses `1.1.0`; MSI product metadata uses the same version `1.1.0`.

macOS CI builds separate Apple Silicon (`arm64`) and Intel (`x64`) DMGs alongside Windows MSI validation on PRs targeting master, pushes to master and manual dispatch. Each DMG contains `puritywallet.app` and an Applications shortcut. Before uploading the DMG and SHA-256 as Actions artifacts, CI mounts the image and verifies the application's version, architecture, bundle identity and ad hoc signature. These builds do not require Apple credentials and are not notarized.

Signing private keys, certificates containing private keys, passwords and notarization credentials must remain outside GitHub repository contents, Secrets, logs and artifacts. Official signing and notarization run locally. Both automatic and manually dispatched GitHub packaging workflows use credential-free ad hoc macOS signing only.

Push the CI configuration to master to trigger installer generation. Official signing and notarization are deferred until the Apple Developer account's organization conversion is complete; keys and passwords remain on the local machine.

# Product specification

## REQ-010: Automatic PR validation — adopted
GitHub Actions validates opened, reopened and updated PRs targeting master and pushes to master. Tests runs the pinned Java 25 regression suite and desktop tests under Xvfb with isolated test homes. Windows Installer runs regression and packaging checks, builds an unsigned MSI, installs it and verifies the installed version. Both workflows support manual dispatch, use read-only repository permissions and retain test reports. CI acceptance requires successful runs for the current PR revision; a rejected dispatch or an empty checks list is not a pass. No release publication or dependency-source changes are included.

## REQ-001: PurityWallet brand — adopted
Use PurityWallet for visible application, CLI and package names. Derive desktop icons from the mobile blue master. Preserve Java packages, dependency coordinates, application identities, storage paths, wallet formats, upstream attribution and licenses. Help, issues and releases use https://github.com/PurityWallet/PurityWalletPC. Disable upstream automatic version checks and hide their switch; offer manual releases access.

## REQ-002: Purity defaults — adopted
The only bundled public Electrum endpoint is ssl://electrum.bitcoinpurity.org:50002 (mainnet). The only predefined explorer is https://mempool.bitcoinpurity.org; custom explorers and disabling exploration remain available. Preserve explicit custom Electrum/Core connections. Never retry legacy bundled public peers. Use connected-node fee estimates and retain manual fees; do not fetch Bitcoin third-party fees. Hide fiat settings and valuations and stop exchange requests even with saved configurations. No Purity Signet support is added; startup remains limited to mainnet and development regtest.

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
Adapt mainnet validation in this application without changing dependency sources or disabling verification. Use the common checkpoint at 961631 and the consensus-pinned Purity activation block at 961637. Validate the pre-activation headers against the shared target and subsequent headers against Purity Core ASERT (anchor 961632, anchor-parent time at 961631, spacing 600 seconds, half-life 86400 seconds). Preserve Core's activation-only harder-target allowance alongside the exact activation hash. Enforce linkage, claimed PoW/powLimit, seeded eleven-header MTP, future-time bound, Merkle inclusion and cumulative-work reorg checks. Historical proof anchors must come from the same application profile. On first wallet fetch, recheck every stored proof above 961631, including records older than the recent reorg window. Isolate only the mainnet header cache by chain/version; preserve wallet paths and identities. Non-mainnet validation delegates to the unchanged dependency. Complete offline regression, full live header synchronization/replay and transaction-proof acceptance before removing the recorded blocker.

## REQ-011: Legacy-chain UTXO and replay warnings
On mainnet, check every UTXO against the Bitcoin legacy chain. Mark an output as shared only when txid, output index, amount and script match an unspent legacy output. Show a prominent warning for each shared output and distinguish checking and unavailable results. On Send, check the actual transaction inputs after coin selection (including automatic selection). Warn clearly when all inputs are shared. Require explicit acknowledgement of replay risk before signing or broadcasting; cancellation must stop the action. An unavailable or malformed check must remain unknown and require an explicit risk acknowledgement to continue. Recheck before protected actions, discard stale results after input/transaction changes, respect offline mode and the configured proxy, and keep regtest free of legacy-mainnet requests. Use the mobile wallet's read-only mempool.space API approach without changing dependency sources or wallet formats.
