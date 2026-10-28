# PurityWallet Product Specification

## Identity and artwork

Full and short name: **PurityWallet**. Command: `puritywallet`.
Release version: **1.0.0b**. Use **1.0.0** in native installer and bundle fields that require numeric versions.
Website: https://bitcoininstitute.com/PurityWalletPC.

Retain internal Java package names and compatible third-party import identifiers.
Use the existing blue-and-white PurityWallet logo from the local mobile project, scaled to the required sizes, for application icons, system tray icons, welcome and about screens, dialogs, notifications and PurityWallet wallet-model views.
Keep other wallet vendors' artwork and hardware-wallet menu names unchanged.

## Bitcoin Purity mainnet

Default Electrum: `ssl://electrum.bitcoinpurity.org:50002`.
Explorer, fees and fallback broadcast: https://mempool.bitcoinpurity.org.
Preserve shared addresses, PSBT, `bitcoin:` payment URIs and amount encoding.

Activation height: **961637**.
Pinned activation hash: `0000000000000000003ea74f4dafdda7ed4e02c4c1ccb9768e0ca4f9e1a35159`.
ASERT anchor height: **961632**; target block spacing: **600 seconds**; half-life: **86400 seconds**.

Keep Bitcoin-only checkpoints and services out of the Purity mainnet path, and do not display BTC market prices as Purity prices.
Bitcoin test networks without Purity parameters cannot start as Purity networks. Keep regtest for development.

## Distribution and licensing

Desktop and headless editions both use the PurityWallet product name and the `puritywallet` launcher.
Retain the upstream Apache 2.0 LICENSE and copyright notices. Distributions include origin and modification notices.
Use multi-resolution PurityWallet icons in macOS ICNS, Windows ICO and Linux PNG resources.

## Documentation

All project documentation, including requirements, architecture, API contracts, migration reports and task records, is written in English.

Windows x64 releases include an MSI installer built on Windows. The installer filename uses `1.0.0b`; MSI product metadata uses numeric version `1.0.0`.
