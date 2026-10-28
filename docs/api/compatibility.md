# API and compatibility contract

No external API, wallet serialization, PSBT format or public URI scheme changes. Java packages, application IDs, Bundle ID, default/XDG/custom data paths and storage keys retain their existing values. No wallet migration occurs; coexistence with upstream Sparrow is not guaranteed.

Help: https://github.com/PurityWallet/PurityWalletPC#readme
Issues: https://github.com/PurityWallet/PurityWalletPC/issues
Manual releases: https://github.com/PurityWallet/PurityWalletPC/releases

Public Electrum: ssl://electrum.bitcoinpurity.org:50002, mainnet only. Explorer: https://mempool.bitcoinpurity.org with existing transaction URL/custom-template rules. Explicit custom Electrum/Core and custom explorer values remain supported. Old public-peer selections are replaced; private endpoints are preserved. Effective fee/exchange/update policy overrides old preferences without changing serialized enum names.

Donation uses the existing wallet selection and Send events with an amount-free payment URI. PURITY_WALLET_DONATION_ADDRESS is a source constant initially set to REPLACE_WITH_PURITYWALLET_DONATION_ADDRESS. Invalid configuration prevents entering Send. Selection cancellation posts no Send events.
