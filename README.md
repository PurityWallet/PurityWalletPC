<!-- Modified for PurityWallet on 2026-10-27; see LICENSE and NOTICE. -->
# PurityWallet

Current release version: **1.0.0b**. Native installer metadata uses numeric version **1.0.0**.

PurityWallet is a desktop wallet for the **Bitcoin Purity** blockchain, derived from Sparrow Wallet. Its full and short product names are both **PurityWallet**.

Website: [bitcoininstitute.com/PurityWalletPC](https://bitcoininstitute.com/PurityWalletPC).
Source and releases: [PurityWallet/PurityWalletPC](https://github.com/PurityWallet/PurityWalletPC).

## Build and run

Use Eclipse Temurin **25.0.2+10** (the release JDK), with `JAVA_HOME` pointing to it.

```sh
git clone --recursive https://github.com/PurityWallet/PurityWalletPC.git
cd PurityWalletPC
./gradlew test
python3 src/test/packaging_test.py
./puritywallet
```

On Windows use `puritywallet.bat`. Packaged executables are `puritywallet` / `puritywallet.exe`.

```sh
./puritywallet --help
./puritywallet --version
./puritywallet --dir /path/to/puritywallet
./puritywallet --terminal
./gradlew jpackage
```

Distributions are generated under `build/jpackage`. See [build documentation](docs/reproducible.md).

## Bitcoin Purity

- Public Electrum: `ssl://electrum.bitcoinpurity.org:50002`.
- Explorer, fees and fallback broadcast: [mempool.bitcoinpurity.org](https://mempool.bitcoinpurity.org).
- Private Electrum and Bitcoin Purity Core nodes can also be configured.
- Mainnet headers are anchored to the Purity activation block at **961637** and validated using **ASERT**, with 600-second target spacing and a one-day half-life.
- Bitcoin-compatible addresses, PSBT, `bitcoin:` payment URIs and BTC/sats amount encoding are retained, matching the existing mobile PurityWallet. BTC market-price sources and Sparrow automatic updates are disabled.
- Bitcoin testnet, testnet4 and Signet are not configured as Purity networks; startup rejects them. Development regtest remains available with `--network regtest`.

## Application data

PurityWallet has its own application identity and never automatically imports Sparrow configuration or wallets.

| Platform | Default home |
| --- | --- |
| macOS / Linux | `~/.puritywallet` |
| Windows | `%APPDATA%/PurityWallet` |

The existing XDG directory mechanism is retained with the `puritywallet` directory name. Logs are named `puritywallet.log`. The home override is `-Dpuritywallet.home=...`; the network environment variable is `PURITYWALLET_NETWORK`.

## Brand assets

Application and UI icons reuse the mobile PurityWallet project's transparent blue-and-white logo. See [asset provenance and sizes](docs/branding.md). All project documentation is maintained in English.

## License and attribution

The actual upstream Sparrow Wallet source is licensed under **Apache License 2.0**, not GPLv2. PurityWallet retains the original [LICENSE](LICENSE), upstream attribution and the modification statement in [NOTICE](NOTICE). ASERT-related MIT attribution is included under [THIRD-PARTY-LICENSES](THIRD-PARTY-LICENSES).

Distribution images include these notices in the bundled runtime's `legal/puritywallet` directory. Dependencies retain their own licenses. Internal Java package names, wallet-file magic and hardware-wallet menu names remain compatible with upstream.
