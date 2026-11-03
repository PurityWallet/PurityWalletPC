# TASK-014: Version the signet comparison build as 1.1.1

## Scope

- [x] Record REQ-013 and synchronize current version documentation.
- [x] Update existing version regression expectations before implementation.
- [x] Change application, Gradle and macOS version metadata to 1.1.1.
- [x] Validate packaging metadata and the application version regression.
- [x] Build and verify a local arm64 DMG and record its checksum.

## Boundaries

Wallet logic, network profiles, dependencies and signing policy remain unchanged. The new binary is a comparison artifact, not evidence that Google has cleared either original review submission. Intel packaging requires its native CI runner. No release publication is included.

## Validation

All 13 Python packaging checks passed, as did `PurityChainTest.productIdentityAndPublicEndpoints` on Temurin 25.0.2+10. `:jpackageImage` completed successfully. The local DMG passed image checksum validation and read-only mounted checks for the Applications shortcut, arm64 launcher, both 1.1.1 bundle version fields, `PurityWallet 1.1.1` CLI output and deep/strict ad hoc signature verification. The image was detached after verification. The sandbox initially denied the disk-image service; creation and read-only mounting succeeded outside the sandbox.

Artifact: `build/macos-release/puritywallet-1.1.1-macos-arm64.dmg`.
SHA-256: `84b6c2b8771217e99deb18bc2269355bed784792f6069f8495887d22f414065a`.

The checksum differs from the reviewed 1.1.0 arm64 DMG (`7af4e4dbdff0721fde5d63b186cb4139b7df5e37a2bccbc5a24b70d945335b01`). Chrome download verdicts have not been tested for this local artifact. No commit, push, CI build, Intel artifact or release publication was performed for this version change.

The later signet legacy behavior change and its updated local package are recorded in [TASK-015](TASK-015.md); this earlier version-only DMG does not include that behavior.
