# API and compatibility contract

No external API, wallet serialization, PSBT format or public URI scheme changes. Java packages, application IDs, Bundle ID, the released base PurityWallet default/XDG/custom data paths and storage keys retain their existing values. No wallet migration occurs; the released base independent PurityWallet identity is preserved.

Help: https://github.com/PurityWallet/PurityWalletPC#readme
Issues: https://github.com/PurityWallet/PurityWalletPC/issues
Manual releases: https://github.com/PurityWallet/PurityWalletPC/releases

Public Electrum: ssl://electrum.bitcoinpurity.org:50002, mainnet only. Explorer: https://mempool.bitcoinpurity.org with existing transaction URL/custom-template rules. Explicit custom Electrum/Core and custom explorer values remain supported. Old public-peer selections are replaced; private endpoints are preserved. Effective fee/exchange/update policy overrides old preferences without changing serialized enum names.

Donation uses the existing wallet selection and Send events with an amount-free payment URI. PURITY_WALLET_DONATION_ADDRESS is a source constant initially set to REPLACE_WITH_PURITYWALLET_DONATION_ADDRESS. Invalid configuration prevents entering Send. Selection cancellation posts no Send events.

## Purity mainnet validation
No external RPC/PSBT/wallet API changes. Internal header-store types are application-owned to select chain-specific verification; non-mainnet uses the existing drongo rules. The common historical checkpoints through 961631 remain trusted; higher heights require locally validated forward headers and the fixed activation hash at 961637. Only the header cache gains a Purity/version subdirectory; wallet storage paths and formats are unchanged. Live verification is read-only and uses an isolated test home.

Every newly connected mainnet Electrum peer is checked against the fixed activation hash even when local headers are cached. Initial mainnet wallet fetches recheck all stored post-fork proofs so old Bitcoin block hashes cannot inherit proof status through shared wallet storage.
