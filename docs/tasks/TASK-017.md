# TASK-017: Run every PR check from one CI workflow

## Scope

- [x] Add REQ-015 and synchronize the specification, architecture, API contract, build documentation and README.
- [x] Update the packaging checks first so that they expect one `ci.yaml` with `tests`, `windows-installer` and `macos-dmg` jobs and no Intel runner.
- [x] Merge the Tests, Windows Installer and macOS DMG workflows into `.github/workflows/ci.yaml` and remove the three files.
- [x] Drop the Intel macOS job from CI.
- [x] Keep every job free of GitHub workflow tokens so it runs on self-hosted Buildkite agents.

## Behavior

The jobs keep their previous steps, triggers, permissions, step-level `PWC_PACKAGES_TOKEN` secret and artifact names; the macOS job builds only the Apple Silicon DMG. The manual Package and scheduled Dependency Verification workflows are unchanged. A CI service that runs one workflow per build, such as Buildkite running GitHub Actions workflows, now covers every PR check in one build.

## Validation

`python3 src/test/packaging_test.py` passed all 19 checks, and every bash step of `ci.yaml` passed `bash -n`. No hosted CI run is claimed: GitHub Actions is disabled for the account, and the Buildkite run is pending.

Buildkite build 1 first failed in every job with "GitHub workflow token request was rejected", and with tokens disabled for the pipeline it failed with "Workflow access tokens are disabled for this pipeline ... Workflow requested contents:read". Buildkite issues workflow tokens only to its hosted agents, while this pipeline uses self-hosted agents. The request came from `actions/setup-java`, whose `token` input defaults to `github.token`; the steps now pass an empty token. Checkout of the public repository and its submodules needs no token.

Build 2 then ran every job on the self-hosted agents. The macOS job passed the regression tests, built and verified the Apple Silicon DMG and loaded the PWC native library from the packaged runtime, but the check's endpoint path under the runner temporary directory exceeded the 104-byte macOS socket limit; the check now uses a short `mktemp -d /tmp/pwc.XXXXXX` directory. The Linux agent runs Ubuntu 24.04, so the desktop runtime packages are now chosen by release. The Windows agent lacks WiX Toolset 3, which must be installed on that agent. Buildkite build 3 passed the Linux tests and the macOS job, including the PWC library check. With WiX 3.14 installed, the Windows job still could not find `candle.exe`, because Buildkite's GitHub Actions runtime does not pass the agent's `WIX` variable to steps; the step now falls back to the machine-level `WIX` variable. In build 4 the Windows job found WiX and ran the 434 regression tests; one PWC text test expected a Unix path separator and now builds its expected value from the same `Path`. In build 5 the Windows job built and installed the MSI (`PurityWallet 1.1.1`) but its PWC check hung: with SDK 0.2.0 the demo listens on the Windows named pipe instead of exiting, so the check now waits up to 30 seconds for `pwc-provider listening on` and then stops the process. Build 6 then failed to install the MSI with 1638 because cancelling build 5 left its test installation on the agent; the job now always uninstalls its own test installation, and only that one.

