# TASK-008: Automatic PR CI and current-revision acceptance

Source: REQ-010 in `input/requirements.md`.

- [x] Attempt manual validation of PR #2 at 03e2a05b before changing workflows.
- [x] Add failing checks for PR triggers and regression/desktop test coverage.
- [x] Add Tests and extend Windows Installer for PR updates and master pushes.
- [x] Validate workflow syntax and local checks (actionlint 1.7.12 and all 13 Python checks passed).
- [x] Push to PR #2 and confirm automatic runs are created.
- [x] Inspect GitHub results and verify both checks succeed on CI implementation revision 7871fd6c; no execution failures required fixes.

Initial dispatch was rejected with HTTP 422, `Actions has been disabled for this user`. Repository Actions is enabled and Windows Installer is active. The mobile repository has the same repository Actions settings and recent runs by the same actor. Automatic PR runs subsequently succeeded, so this manual-dispatch error does not establish an organization-wide or automatic-CI restriction. Its upstream cause remains unconfirmed.

## Verified GitHub results

- Source revision: 7871fd6cc3c9b50d5532fe6fde082fc432540faf.
- [Tests](https://github.com/PurityWallet/PurityWalletPC/actions/runs/40650559307): 372 regression tests and 3 desktop tests passed, with no failures or skipped tests.
- [Windows Installer](https://github.com/PurityWallet/PurityWalletPC/actions/runs/40650559299): 371 regression tests passed; the existing Windows-inapplicable `loadsAStoreThatCannotBeDeleted` test was skipped by its pre-existing platform assumption. All 13 Python checks, MSI build, MSI installation and installed `PurityWallet 1.0.0b` version check passed.
- The workflows, Java code, dependency revisions and verification rules required no further changes after the successful CI runs. This acceptance-record update will also trigger the same checks.
- The Windows installer remains unsigned and is a CI artifact, not a published release.
- Downloaded installer `puritywallet-1.0.0b-windows-x64.msi`: 100,341,580 bytes, SHA-256 `edf9074615217ecba8211451cf262d27925059ec814009788458d40311d72f8b`. The MSI checksum file, MSI container signature and GitHub artifact ZIP digest were verified.
