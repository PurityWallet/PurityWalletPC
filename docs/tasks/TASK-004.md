# TASK-004: Build a Windows x64 MSI Installer

Source: `input/requirements.md`.

- [x] Inspect local build capabilities and existing packaging configuration.
- [x] Add a failing Windows-workflow regression check before creating the workflow.
- [x] Prepare a Windows-only build using the pinned JDK and WiX 3.x template.
- [x] Include MSI installation, installed version verification and SHA-256 generation.
- [ ] Push the current sources to `codex/puritywallet-windows-1.0.0b` (user approval received).
- [ ] Complete the Windows build, inspect any failures and download the MSI.

Status: the user authorized publishing the current sources on the dedicated branch and building with GitHub Actions. The Windows build is in progress. The current host is macOS ARM64; jpackage cannot create a native Windows MSI on it.

Local validation: all eleven Python checks passed; actionlint accepted the workflow, and the PowerShell parser accepted all four run scripts. GitHub Actions is enabled for the repository. The user authorized publication and the native Windows build.
