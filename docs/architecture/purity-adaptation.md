# Purity adaptation architecture

The JavaFX application remains in com.sparrowwallet.sparrow. The released base PurityWallet identity and independent storage paths are retained. No dependency sources are changed by the adaptation.

Config provides effective node-only fees, ExchangeSource.NONE and disabled upstream updates regardless of saved preferences. Only the Purity mainnet public peer is bundled; private Electrum and Core use existing connection paths. Network validation, transaction verification and checkpoints must not be bypassed.

Donation reuses open-wallet eligibility and Send events. A fixed recipient is validated before selection and no amount is supplied. Existing software, hardware and external PSBT signing remain responsible for final authorization.

Desktop transaction notifications and LNURL-auth remain unchanged except visible branding. Remote notification registration is not added.

## Mainnet chain verification
Application-owned ChainCheckpoints and HeaderState select Purity mainnet policy; other networks adapt the original dependency validator. PurityHeaderState authenticates the eleven-header median-time seed against shared checkpoint 961631, checks the five pre-activation headers and retains ASERT anchor 961632. Activation 961637 must match the fixed Core consensus hash. Every subsequent header is validated with integer ASERT, linkage, PoW/powLimit, MTP, future-time and real chain work.

ElectrumServer and HeaderStore obtain all proof/sync/reorg anchors through this profile. Mainnet cache files live under headers/purity-mainnet-asert-v1; existing Bitcoin cache files are neither trusted nor removed. Every new mainnet Electrum connection must supply the fixed activation header even when the local cache is populated, and substantiate the activation chain before wallet histories are accepted. No resource shadowing, reflection, bytecode patching or weakened validation is used.

Because wallet identities and formats remain shared with upstream, mainnet's first wallet fetch compares every stored post-fork proof against the Purity store, rather than only the latest 100 blocks. A Bitcoin-era block hash therefore cannot retain proof status just because it is old. Non-mainnet keeps the existing recent-reorg window.
