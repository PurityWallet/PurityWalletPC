# TASK-001: PurityWallet Rebrand and Bitcoin Purity Mainnet Migration

Source: `input/requirements.md`.

- [x] Add regression tests for branding, packaging and chain validation before implementation.
- [x] Update product names, website, launchers and installers.
- [x] Isolate data directories and logs.
- [x] Configure Purity Electrum / Esplora, checkpoints and ASERT validation.
- [x] Include Apache 2.0 origin and modification notices in distributions.
- [x] Compile, test and synchronize documentation.

Validation: 352 Java tests, one live checkpoint verification and three Python packaging checks passed.
The macOS application image was built. Its launcher `--version` / `--help`, application identity and bundled LICENSE / NOTICE / MIT notices were checked.
Windows and Linux packaging configuration was updated but was not built or run on those platforms.

Artwork replacement and the English documentation follow-up are tracked in [TASK-002](TASK-002.md).
