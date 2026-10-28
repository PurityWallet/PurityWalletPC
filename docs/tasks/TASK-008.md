# TASK-008: Automatic PR CI and current-revision acceptance

Source: REQ-010 in `input/requirements.md`.

- [x] Attempt manual validation of PR #2 at 03e2a05b before changing workflows.
- [x] Add failing checks for PR triggers and regression/desktop test coverage.
- [x] Add Tests and extend Windows Installer for PR updates and master pushes.
- [x] Validate workflow syntax and local checks (actionlint 1.7.12 and all 13 Python checks passed).
- [ ] Push to PR #2 and confirm automatic runs are created.
- [ ] Repair observed GitHub failures and verify both checks succeed on the latest revision.

Initial dispatch was rejected with HTTP 422, `Actions has been disabled for this user`. Repository Actions is enabled and Windows Installer is active. The mobile repository has the same repository Actions settings and recent runs by the same actor, so the error alone does not establish an organization-wide restriction. GitHub acceptance remains pending until runs actually execute successfully.
