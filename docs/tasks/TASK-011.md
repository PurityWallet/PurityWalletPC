# TASK-011: Unify Release Version 1.1.0 and Correct the Website

## Scope

- [x] Use `1.1.0` for the application, Gradle archives, jpackage and macOS bundle metadata.
- [x] Show only one release version in the README.
- [x] Replace the old product website with `https://bitcoininstitute.com.au` in the application, Linux package metadata and current documentation.
- [x] Update the GitHub repository About website to the same URL.
- [x] Synchronize requirements, product specification, architecture, API contract and task index.

## Validation

The updated packaging version test failed against the previous release configuration, then all 13 Python packaging checks passed after implementation. The Java `PurityChainTest.productIdentityAndPublicEndpoints` regression passed on Eclipse Temurin 25.0.2+10. A Gradle assertion verified resolved application/archive and jpackage versions are both `1.1.0`. The edited XML files parse successfully and `git diff --check` passed. The GitHub About website was read back as `https://bitcoininstitute.com.au`. Source and README changes remain local and uncommitted; no release installer has been built or published for this change.
