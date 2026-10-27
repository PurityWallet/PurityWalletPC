# PurityWallet Desktop Requirements

- Both the full and short product names are PurityWallet, derived from Sparrow Wallet.
- Use Bitcoin Purity mainnet, taking chain and service parameters from the local BitcoinPurity and PurityWallet projects.
- Website: https://bitcoininstitute.com/PurityWalletPC.
- Command and executable names: puritywallet; puritywallet.exe and puritywallet.bat on Windows.
- Use independent PurityWallet configuration, wallets, logs and instance identifiers.
- As confirmed by the user, retain the actual upstream Apache 2.0 license and copyright notices rather than changing to GPLv2.
- Change only this project. Do not modify dependency sources, including drongo and lark.
- Replace application icons and Sparrow artwork throughout the UI with the existing brand assets from the local PurityWallet project.
- Write all project documentation in English.
- The PurityWallet release version is 1.0.0b. Use 1.0.0 in native fields that require numeric versions.
- Provide a Windows x64 MSI installer for release 1.0.0b, built from the current PurityWallet sources on Windows.
