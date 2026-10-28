# Purity header fixture

`purity-mainnet-961632-963650.bin` contains 2019 consecutive raw 80-byte public block headers fetched read-only from `ssl://electrum.bitcoinpurity.org:50002` on 2026-10-28. SHA-256: `884c2a31637d6360b4342a2b9a9e18b9f72ecbe2eefd19926c0d208cff2edd3c`.

The fixture is untrusted input to the validator: it must link to the shared 961631 checkpoint, meet the Core activation hash at 961637 and satisfy expected difficulty and PoW. It crosses the former conflicting Bitcoin pin at 963647 and the following two distinct ASERT targets.

The application median-time seed at `/purity/mainnet-anchor.hex` contains heights 961621–961631. Its fields are authenticated by contiguous hash linkage ending at the hardcoded common checkpoint, not by trusting the download source. Its file SHA-256 is `288b8f52b5b4bf8df62c7852c8057be5a6c1edd5d378f2a5397a29ddba3d958f`.

ASERT reference vectors in `PurityHeaderStateTest` derive from Bitcoin Purity Core `src/test/pow_tests.cpp`, `signet_asert_integer_reference`, scaled by 24 bits into the mainnet target domain. The arithmetic uses the original mainnet ASERT formula; this does not add Purity Signet support.
