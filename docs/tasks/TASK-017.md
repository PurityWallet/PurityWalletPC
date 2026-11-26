# TASK-017: Run every PR check from one CI workflow

## Scope

- [x] Add REQ-015 and synchronize the specification, architecture, API contract, build documentation and README.
- [x] Update the packaging checks first so that they expect one `ci.yaml` with `tests`, `windows-installer` and `macos-dmg` jobs and no Intel runner.
- [x] Merge the Tests, Windows Installer and macOS DMG workflows into `.github/workflows/ci.yaml` and remove the three files.
- [x] Drop the Intel macOS job from CI.

## Behavior

The jobs keep their previous steps, triggers, permissions, step-level `PWC_PACKAGES_TOKEN` secret and artifact names; the macOS job builds only the Apple Silicon DMG. The manual Package and scheduled Dependency Verification workflows are unchanged. A CI service that runs one workflow per build, such as Buildkite running GitHub Actions workflows, now covers every PR check in one build.

## Validation

`python3 src/test/packaging_test.py` passed all 18 checks, and every bash step of `ci.yaml` passed `bash -n`. No hosted CI run is claimed: GitHub Actions is disabled for the account, and the Buildkite run is pending.
