# Product specification

## REQ-001: PurityWallet brand — adopted
Use PurityWallet for visible application, CLI and package names. Derive desktop icons from the mobile blue master. Preserve Java packages, dependency coordinates, application identities, storage paths, wallet formats, upstream attribution and licenses. Help, issues and releases use https://github.com/PurityWallet/PurityWalletPC. Disable upstream automatic version checks and hide their switch; offer manual releases access.

## REQ-002: Purity defaults — adopted
The only bundled public Electrum endpoint is ssl://electrum.bitcoinpurity.org:50002 (mainnet). The only predefined explorer is https://mempool.bitcoinpurity.org; custom explorers and disabling exploration remain available. Preserve explicit custom Electrum/Core connections. Never retry legacy bundled public peers. Use connected-node fee estimates and retain manual fees; do not fetch Bitcoin third-party fees. Hide fiat settings and valuations and stop exchange requests even with saved configurations. No Purity Signet support is added.

## REQ-003: In-wallet donations — adopted
Donate in About selects an eligible open wallet, including hardware, multisig and watch-only/PSBT workflows. Always ask, even with one wallet. One replaceable recipient constant initially contains an invalid placeholder; display an unavailable message until valid. A configured recipient opens the normal Send view with no amount. Cancellation or no eligible wallet makes no payment. Never automatically construct, sign or broadcast a transaction.

## REQ-004: Hide Lightning creation — already satisfied
There are no Lightning, LNDHub or Ark wallet creation entries. Preserve LNURL-auth and lightning: handling.

## REQ-005: Android identity — not adopted
Android-only. Do not introduce desktop identity or storage isolation. Coexistence with Sparrow is not guaranteed and no automatic data migration is performed.

## REQ-006: Simplified creation — not adopted
Keep full desktop single-signature, multisig, script-type and hardware configuration. Keep native SegWit single-signature as the existing default. Preserve imports and existing wallets.

## REQ-007: iOS identity — not adopted
Do not introduce iOS extensions/App Groups or change the macOS Bundle ID.

## REQ-008: Mobile push service — not adopted
Keep existing desktop transaction notifications and their switch, updating visible brand only. Do not register mobile push tokens or connect a remote push backend.

## Acceptance criteria
Fresh and reset defaults offer only Purity services. Explicit custom connections survive configuration reload. Saved external fee, exchange and update preferences cannot activate their services. Manual fees and desktop notification preferences remain available. Rebranding must leave default/XDG/custom storage locations and serialized wallet formats unchanged. Icons and names cover Windows, macOS and Linux. Donation always requires selection and the existing payment review/signing workflow; the shipped placeholder cannot enter payment creation.

## REQ-009: Purity mainnet header verification — adopted
Adapt mainnet validation in this application without changing dependency sources or disabling verification. Use the common checkpoint at 961631 and the consensus-pinned Purity activation block at 961637. Validate the pre-activation headers against the shared target and subsequent headers against Purity Core ASERT (anchor 961632, anchor-parent time at 961631, spacing 600 seconds, half-life 86400 seconds). Preserve Core's activation-only harder-target allowance alongside the exact activation hash. Enforce linkage, claimed PoW/powLimit, seeded eleven-header MTP, future-time bound, Merkle inclusion and cumulative-work reorg checks. Historical proof anchors must come from the same application profile. On first wallet fetch, recheck every stored proof above 961631, including records older than the recent reorg window. Isolate only the mainnet header cache by chain/version; preserve wallet paths and identities. Non-mainnet validation delegates to the unchanged dependency. Complete offline regression, full live header synchronization/replay and transaction-proof acceptance before removing the recorded blocker.
