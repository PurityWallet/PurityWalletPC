# Migration Report

## Product and chain migration

The source project is Sparrow Wallet 2.5.6. Its actual LICENSE is Apache 2.0; the user confirmed retaining that license.
The inherited defaults used Bitcoin Electrum, Esplora and price services, plus Sparrow's update service. Inapplicable services have been replaced or disabled.

Bitcoin Purity and Bitcoin share their genesis block, address encoding and transaction formats. Genesis verification alone cannot distinguish the fork. Mainnet validation therefore pins activation block 961637 and uses Purity ASERT.

Local parameter sources:

- `bitcoinpurity/src/kernel/chainparams.cpp`
- `bitcoinpurity/src/consensus/params.h`
- `bitcoinpurity/src/pow.cpp`
- `PurityWallet/blue_modules/BlueElectrum.ts`
- `PurityWallet/models/blockExplorer.ts`

Internal package names, hardware-wallet menu references and compatible import identifiers retain their upstream meaning. User-visible product names, installation paths and executables use PurityWallet / puritywallet.
Sparrow wallets and configuration are not migrated automatically, keeping chain data and service settings separate.

Purity Core uses 1.x version encoding (`1.0.0 = 10000`), so the Bitcoin Core `>=240000` version check cannot be reused for it. The mainnet Core adapter validates the Purity subversion and activation block before initializing wallets.

The first migration passed 352 Java tests, one live Purity Electrum checkpoint verification and three Python packaging checks. Its macOS image was built and checked for launcher identity and bundled license files. No dependency source was modified. Windows and Linux configuration received static checks; native builds and execution were not verified.

## Brand artwork and English documentation

Reuse the existing blue-and-white brand assets from the local mobile PurityWallet project. The canonical logo is copied without modifying its artwork. Desktop PNGs are scaled from that transparent master; ICNS and ICO containers wrap these PNGs. The mobile project's opaque native icons are not used, avoiding black rectangles in light themes.
Replace all Sparrow application artwork and route branded JavaFX dialogs and wallet-model views to the PurityWallet PNG. Preserve internal `WalletModel.SPARROW` values for wallet-file compatibility and retain other vendors' artwork.

Translate the requirements intake, product specification, architecture, API contract, migration report and task documents into English. Existing English README, security and reproducible-build documents remain in English.

Asset provenance is recorded in [Brand assets](branding.md). Verification for this follow-up is recorded in [TASK-002](tasks/TASK-002.md).

The artwork follow-up passed 352 Java regression tests and nine Python checks. Light and dark JavaFX renders were visually inspected, including dialogs, wallet-model views, window icons and tray icons. The macOS application and ZIP were rebuilt and checked for the transparent native icon and current runtime resources. Windows and Linux native execution remains unverified.

## Initial PurityWallet release version

The initial PurityWallet release version is `1.0.0b`, replacing the inherited product version `2.5.6`. Upstream attribution still identifies Sparrow Wallet 2.5.6. The application, banners and archive filenames use `1.0.0b`; native metadata uses numeric `1.0.0`. The download verifier retains the beta suffix when locating release manifests and signatures.
