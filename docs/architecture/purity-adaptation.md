# Purity adaptation architecture

The JavaFX application remains in com.sparrowwallet.sparrow. Visible product identity is separate from the existing Sparrow storage identity. No dependency source or application identity changes are allowed.

Config provides effective node-only fees, ExchangeSource.NONE and disabled upstream updates regardless of saved preferences. Only the Purity mainnet public peer is bundled; private Electrum and Core use existing connection paths. Network validation, transaction verification and checkpoints must not be bypassed.

Donation reuses open-wallet eligibility and Send events. A fixed recipient is validated before selection and no amount is supplied. Existing software, hardware and external PSBT signing remain responsible for final authorization.

Desktop transaction notifications and LNURL-auth remain unchanged except visible branding. Remote notification registration is not added.
