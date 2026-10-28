# TASK-010: Set the Release Version to 1.0.0b3

Source: the release-version requirement in `input/requirements.md`.

The latest user request replaces the earlier local `1.1.0b` version with `1.0.0b3`.

- [x] Update the existing application and packaging version checks before implementation; the packaging check failed against the old version.
- [x] Set application and Gradle archive versions to `1.0.0b3`, with macOS native metadata `1.0.0`.
- [x] Strip both `b` and numbered beta suffixes such as `b3` when deriving numeric jpackage versions. Preserve the full beta version in application output and archive filenames.
- [x] Synchronize current requirements, product specification, architecture, API contract and build documentation. Preserve historical release and CI evidence.
- [x] Verify the version regression, packaging checks and resolved native package version.

This task does not build or publish a release installer.

Validation: all 13 Python packaging checks and the Java `PurityChainTest.productIdentityAndPublicEndpoints` regression passed. A Gradle assertion verified application/archive version `1.0.0b3` and resolved jpackage version `1.0.0`. `git diff --check` passed.
