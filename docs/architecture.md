# Architecture

PurityWallet retains Sparrow's JavaFX UI, Gradle build, Electrum / Core RPC adapters and wallet persistence architecture.
Application constants in `SparrowWallet` define the product identity. Internal Java package names remain compatible with upstream.

Purity checkpoints and ASERT header verification live in this project's `net` package and are used by `HeaderStore` and `ElectrumServer`.
The locked drongo and lark revisions are used as dependencies without modifying their sources.
Network presets use Bitcoin Purity services; application data and logs use independent `puritywallet` directories.

Application and UI artwork comes from the local mobile PurityWallet project. JavaFX `ImageView` renders the canonical PNG in branded dialogs and wallet-model views; other wallet vendors retain their own SVG artwork.
Native icon containers include scaled versions of the existing transparent mobile logo at platform-specific sizes. See [Brand assets](branding.md) for provenance.

All project documentation is written in English.

The application and Gradle archive version are `1.0.0b`. Native package metadata strips the beta suffix to use numeric version `1.0.0`.

PR CI uses separate Tests (Linux, with Xvfb for JavaFX) and Windows Installer workflows. Both run for PRs targeting master and pushes to master, with manual dispatch retained. Tests use separate build-directory application homes. The Windows job builds and installs the unsigned MSI and checks its executable version. Test reports and installer checksums are uploaded as CI artifacts; no release is published.
