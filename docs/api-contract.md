# External API Contract

- Product website: https://bitcoininstitute.com/PurityWalletPC.
- Electrum JSON-RPC over TLS: `electrum.bitcoinpurity.org:50002`; client name `PurityWallet`.
- Bitcoin Purity Core: compatible with Bitcoin Core RPC. The user selects the node; the pinned Purity activation block validates chain identity before wallet initialization.
- Esplora REST: https://mempool.bitcoinpurity.org/api/ for fees, block data and fallback transaction broadcasting.
- Preserve the protocol names for `bitcoin:` payment URIs, PSBT and compatible wallet import formats.
- Do not use Sparrow's update-signing identity or Bitcoin market-price services as a source of Purity prices.

Brand assets and documentation language changes do not change these external interfaces.

The application reports version `1.0.0b`, including in its local Electrum adapter banner. Native installer metadata uses numeric version `1.0.0`.
