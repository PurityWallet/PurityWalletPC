# TASK-004: Build a Windows x64 MSI Installer

Source: `input/requirements.md`.

- [x] Inspect local build capabilities and existing packaging configuration.
- [x] Add a failing Windows-workflow regression check before creating the workflow.
- [x] Prepare a Windows-only build using the pinned JDK and WiX 3.x template.
- [x] Include MSI installation, installed version verification and SHA-256 generation.
- [x] Push the current sources to `codex/puritywallet-windows-1.0.0b` (user approval received).
- [x] Complete the Windows build, inspect any failures and download the MSI.

Status: completed. GitHub Actions built the installer on Windows Server 2022 and successfully installed it on the runner. The installed executable reported `PurityWallet 1.0.0b`.

Local validation: all eleven Python checks passed; actionlint accepted the workflow, and the PowerShell parser accepted all four run scripts. GitHub Actions is enabled for the repository. The user authorized publication and the native Windows build.

## Verified output

- Source commit: `e47afe4d7a8441fe3d0ce1300fdba736d1b746e7`.
- [Successful Windows build](https://github.com/PurityWallet/PurityWalletPC/actions/runs/40490528703).
- Filename: `puritywallet-1.0.0b-windows-x64.msi`.
- Size: 99,608,396 bytes.
- SHA-256: `8278a7acb514a2e96c5e3cda5c3fdca117ef6145350fa882c973b27cae51373a`.
- The Windows job passed the Java test task and all eleven Python checks, then built, installed and verified the MSI.
- The downloaded artifact ZIP matched GitHub's SHA-256 digest; the extracted MSI matched its accompanying checksum file.
- The MSI is unsigned. Native installer product metadata uses `1.0.0`; the application release version is `1.0.0b`.
