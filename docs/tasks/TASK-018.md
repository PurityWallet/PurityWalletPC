# TASK-018: Report test coverage in CI

## Scope

- [x] Add REQ-016 and synchronize the specification, architecture, API contract and task index.
- [x] Update the packaging checks first so that they expect coverage in every CI job.
- [x] Apply the JaCoCo plugin to the `:test` suite only, with HTML and XML reports and a printed line and branch summary.
- [x] Add the JaCoCo artifacts to dependency verification.
- [x] Run the coverage report and upload it in the `tests`, `windows-installer` and `macos-dmg` jobs.

## Behavior

See REQ-016. Coverage is reported, not enforced, and stays within CI artifacts.

## Validation

`python3 src/test/packaging_test.py` passed all 20 checks. With JaCoCo 0.8.16 on OpenJDK 25.0.5 (Linux x86_64), `./gradlew :test :jacocoTestReport --dependency-verification strict` ran 434 tests (one Intel-Mac-only test skipped), wrote the HTML and XML reports to `build/reports/jacoco/test/` and printed line coverage 11.9% (5336 of 44890) and branch coverage 11.9% (2460 of 20598). `desktopTest` still passes and writes no coverage data. The JaCoCo artifacts are verified by the signing key `A413F67D71BEEC23ADD0CE0ACB43338E060CF9FA` (Evgeny Mandrikov, code signing key), now in the verification keyring; Gradle also recorded the `asm-bom` 9.11.1 checksum and trusted the existing ASM key for the `org.ow2.asm` group, which JaCoCo depends on. In Buildkite build 9 of `cbf0d28` all three jobs passed and uploaded their coverage artifacts: Linux and macOS reported line coverage 11.9% (5337 of 44890) and branch coverage 11.9% (2461 of 20598), and Windows 11.9% (5323 of 44890) and 11.9% (2457 of 20598), since a few platform-specific tests take other paths there.
