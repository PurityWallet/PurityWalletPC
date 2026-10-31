# TASK-012: Build macOS DMGs in CI

## Scope

- [x] Add a macOS DMG workflow for PRs targeting master, pushes to master and manual dispatch, alongside the Windows installer workflow.
- [x] Build separate Apple Silicon and Intel DMGs using native macOS runners and the pinned Temurin JDK.
- [x] Run regression and packaging checks before building; verify the mounted app's version, architecture, bundle metadata and ad hoc signature before upload.
- [x] Upload versioned DMGs, SHA-256 files and test reports as Actions artifacts without Apple credentials or notarization.
- [x] Remove the legacy manual Package workflow's Apple signing Secrets references and notarization dependency; keep signing private keys, passwords and notarization credentials outside GitHub.
- [x] Synchronize requirements, product specification, architecture, API contract, build instructions and migration report.

## Validation

The existing Gradle build passed but the initial DMG acceptance check failed because no DMG was produced. After adding the workflow, its exact create-and-verify shell steps produced `build/macos-release/puritywallet-1.1.0-macos-arm64.dmg` locally and passed image checksum verification, read-only mounting, the Applications shortcut check, launcher architecture, both bundle versions, bundle identity, deep code-signature verification and the packaged executable's version check. The SHA-256 sidecar verified successfully.

All 378 Java regression tests and 13 Python packaging checks passed. actionlint passed for both changed workflows; embedded shell scripts passed `bash -n`; `git diff --check` passed. No signing Secrets references remain in workflows, and a read-only GitHub check reported zero repository-level Secrets. No signing credentials were uploaded. Hosted CI and Intel validation are pending at the time of this change and will run on the authorized push to master. No public GitHub Release is created by this workflow.

The user authorized committing and pushing the CI configuration. Official signing and notarization are deferred until the Apple Developer account's organization conversion is complete; keys and passwords remain on the local machine.
