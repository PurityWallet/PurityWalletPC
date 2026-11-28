# Migration report

## Purity signet restart and defaults — 2026-11-02

The restart menu offered signet even though startup explicitly exited for every network except mainnet and regtest. Startup and the menu now share the supported mainnet/signet/regtest list. Signet defaults to TLS Electrum on `electrum.bitcoinpurity.org:60002` and `https://mempool.bitcoinpurity.org/signet`; transaction links contain exactly one network path. Custom connections/explorer templates, disabled exploration and mainnet defaults are preserved.

The live endpoint serves the Purity fork, not the unchanged Bitcoin signet difficulty profile. The application pins shared parent 327021 and activation 327022, authenticates the eleven-header MTP/ASERT seed by hash linkage and validates the forward chain with the signet PoW limit and exact ASERT. The activation/parent hashes were cross-checked against the deployed Electrum and explorer and the previously archived P2P boundary headers. The seed and 35-header fixture were fetched read-only from the requested TLS endpoint. Core's H-5 anchor, 600-second spacing, 86400-second half-life and activation-only harder-target rule were checked against the local Bitcoin Purity consensus sources. Signet headers use a separate `purity-signet-asert-v1` cache, public proof support remains mandatory when verification is enabled, and saved post-fork proofs are rechecked. Dependency source trees are unchanged.

Validation:

- Java 25 `:test :desktopTest :installDist` passed: **387 regression tests** and **7 desktop tests**, no failures or skips. The first sandboxed desktop attempt could not load/access the graphical runtime; the isolated desktop run outside the sandbox passed, using a temporary JavaFX cache.
- `:verifyCheckpoint --tests '*PuritySignetSyncTest'` passed against `ssl://electrum.bitcoinpurity.org:60002` at 2026-11-02 12:25 Australia/Melbourne (01:25 UTC): **1,851 headers**, 327022–328872, ending at `000000112b8166e817375a95f822dbc8b02608918016c8e29a9cdaae1b5e81cd`, accumulated work `317615789764`. Cache replay matched; the activation transaction's Merkle proof was accepted, a wrong txid was rejected, shared historical checkpoint lookup passed, and verification remained mandatory.
- All **13 Python packaging checks** and `git diff --check` passed. Every tracked file in both dependency directories was byte-compared with its pinned Git archive and matched.

- `:jpackageImage` built the local macOS arm64 app; its launcher reports `PurityWallet 1.1.0`, and deep/strict ad hoc signature verification passed. Launched it with `--network SIGNET`, a new temporary home, offline mode and the global lock-file pointer disabled. It stayed running, completed desktop initialization in `signet/config`, and left the temporary mainnet configuration unchanged; only the exact test process was terminated. The initial smoke assertion expected console logging, but the packaged app does not emit that message there; the final smoke check used actual signet desktop settings and passed.

The local app is `build/jpackage/puritywallet.app`. No commit, push, release publication, real transaction or native Windows/Linux execution was performed. See [TASK-013](tasks/TASK-013.md).

## Scope
Mobile REQ-001/002/003 are adapted; REQ-004 is satisfied; REQ-005/007 are platform-specific and excluded; REQ-006 retains full desktop behavior; REQ-008 retains desktop notifications without mobile push.

## Compatibility
Preserve application identities, storage paths, packages, serialized formats, user custom servers/explorers, wallet imports, LNURL-auth and desktop signing capabilities. Disable external fees, fiat and upstream update checks even with old saved settings. No data is migrated, deleted or rewritten by a migration. The fork does not promise independent coexistence with Sparrow.

## Verification and release boundary
Use Java 25 and pinned submodules; never modify dependency sources. Run regression tests, local build/launch and read-only Purity RPC/history checks. Checkpoint or chain incompatibility blocks live acceptance; do not bypass validation. Signing, notarization, publication and real payments are excluded. Results are recorded below.

## Results — 2026-10-28 (Australia/Melbourne)

### Implemented locally
- PurityWallet display branding, unchanged mobile blue master and Windows/macOS/Linux icon derivatives; existing Java packages, application ID, macOS Bundle ID and Sparrow storage identity retained.
- Purity-only bundled mainnet Electrum and explorer defaults; explicit private Electrum/Core and custom explorer configuration retained. Non-mainnet modes offer manual connection choices without bundled Bitcoin peers.
- Effective node-only fees, no fiat exchange source, and disabled upstream update checks even when old serialized preferences request external services. Desktop fiat/update controls removed; manual fees and desktop notifications retained.
- About Donate validates a single replaceable recipient, always asks for a wallet and reuses amount-free Send events. The shipped placeholder blocks payment. No external API or wallet format changes.
- Project documentation, support and manual releases links replace upstream product links. Local packaging no longer references the upstream developer's signing identity.

### Passed
- Java: Eclipse Temurin 25.0.2+10; pinned drongo `13059ebc2979640b30bc1534b4584e2191578672` and lark `fdcf5a0f0e774bca27a7a08b4e0427465f1dcbac`. Dependency source trees remain unchanged.
- `./gradlew :test`: 354 tests passed, including effective preferences, custom connections, public-peer replacement, invalid recipient and unchanged storage identity.
- `./gradlew :desktopTest`: 3 tests passed in a separate graphical JVM. Software, hardware, multisig and watch-only wallets require explicit selection and emit no preset amount. Cancel, invalid address and no wallet emit no Send events. Settings/About render; previews are under `build/qa/`.
- Mobile master SHA-256 matches `4c77b0cf5d54172a5194ba6f97c616f6848e0f52e3ac3e916630c24a906f0111`; 12 PNG derivatives, 7 ICNS chunks and 6 ICO entries pass format/dimension checks.
- Purity Electrum TLS, `server.version`, header subscription and `blockchain.estimatefee` work. Observed tip: 971147; six-block fee response: 0.00001011 coin/kB.
- Historical height 800000 header hashes to `00000000000000000002a7c4c1e48d76c5a37902165a270156b7a8d72728a054`. Its coinbase transaction `b75ca3106ed100521aa50e3ec267a06431c6319538898b25e1b757a5736f5fb4` returns identical raw bytes from Electrum and the Purity explorer.

### Original live-acceptance blocker — resolved by REQ-009 below
`./gradlew :verifyCheckpoint --tests '*HeaderCheckpointVerificationTest.verifyMainnetCheckpoints'` fails at the pinned height **963647**:

| Source | Block hash |
|---|---|
| Pinned upstream dependency | `00000000000000000000d29ee44c13e924c37abcaa80c537609ce70b1fea1f33` |
| Purity Electrum | `000000000000003e346d906ea62abdf577ede019541e82796f81e243ff907626` |

The original failure was a chain compatibility problem, not a TLS or RPC outage. The unadapted Bitcoin-only validator could not synchronize Purity. No dependency source was edited and no verification was disabled. The authorized application-level resolution and its acceptance evidence are recorded under REQ-009 below.

Windows/Linux installation and execution have not been tested natively on this macOS host; their metadata and assets were checked statically. Hardware tests cover wallet selection and PSBT routing, not a physical device signing session. No real transaction, release signing, notarization, publication or deployment occurred.

### Packaged macOS application
`./gradlew :test :desktopTest :jpackageImage` completed successfully. The bundle is `build/jpackage/PurityWallet.app`; its `CFBundleName`, executable and icon resolve correctly, while `CFBundleIdentifier` remains `com.sparrowwallet.sparrow`. Its bundled runtime reports `PurityWallet 2.5.6` for `--version`.

The packaged application was launched using an isolated temporary home, `mode=OFFLINE`, `loadRecentWallets=false` and `SPARROW_NO_LOCK_FILE_LINK=true`. The process remained running and was then terminated by its exact temporary-home command-line match. No existing wallet instance was operated. Native UI automation matched another instance sharing the Bundle ID and was not used as evidence; the separate JavaFX rendering/interaction tests provide UI verification.

The build emits existing JEP 493/modular-JAR and module-name warnings. No release signing identity or notarization was used; the existing local launcher patch applies only an ad hoc development signature.

## REQ-009 follow-up — 2026-10-28
The user authorized an application-owned Purity validation adapter. The original checkpoint failure remains historical evidence; this follow-up replaces the mainnet validation policy without editing dependency sources or disabling checks. Full acceptance results are recorded below.

### REQ-009 implementation and security behavior
- Application-owned `ChainCheckpoints` caps the mainnet historical prefix at the shared checkpoint 961631. Other networks delegate to the unchanged drongo state validator.
- `PurityHeaderState` authenticates the packaged eleven-header MTP seed by hash linkage to that common checkpoint. It validates pre-activation targets and exact integer ASERT from 961637 using anchor target at 961632 and anchor-parent time at 961631. Core's activation-only harder-target allowance is retained together with the fixed activation hash.
- Header linkage, expected difficulty, claimed PoW/powLimit, seeded MTP, a two-hour future bound and true cumulative work above the anchor are enforced before any header is accepted. Merkle and cumulative-work reorg checks remain enabled.
- Every new Electrum peer must supply the fixed activation header, even with a fully populated local cache. The activation chain is substantiated before the connection's history can proceed.
- Sync, historical inclusion proofs, reorg candidates and cache replay use the same application-selected profile. Mainnet headers use `headers/purity-mainnet-asert-v1`; old Bitcoin header files are neither trusted nor deleted.
- Mainnet's initial wallet fetch compares all saved post-fork proofs, including records outside the former 100-block reorg window. Wallet paths and serialized formats remain unchanged.

### Final read-only mainnet acceptance
Command: `JAVA_HOME=/private/tmp/puritywallet-jdk25/Contents/Home ./gradlew :verifyCheckpoint --tests '*PurityMainnetSyncTest' --console=plain`.

The final adapter passed at **2026-10-28 16:42:52 Australia/Melbourne** (05:42:52 UTC) against `ssl://electrum.bitcoinpurity.org:50002`, Fulcrum 2.1.3 / protocol 1.4.2:

| Check | Result |
|---|---|
| Continuous validated range | 961632–971156, **9525 headers** |
| Announced and locally verified tip hash | `000000000000000061fe16bf6462f70d0879eab9538fe7507e71fed897d5f928` |
| Activation block 961637 | Fixed Core hash matched |
| Former conflict at 963647 | Purity hash `000000000000003e346d906ea62abdf577ede019541e82796f81e243ff907626` validated by the forward chain |
| Accumulated work above common anchor | `2805468777373171182134182` |
| Cache reload/replay | Same height, hash and accumulated work |
| Post-fork transaction inclusion | `25b055c20d7bebd9d48a0a22b13b9bc6e355587b61cde954d7630c79bcd8c5c8` proven at 963647 through the application's normal proof path |
| Negative proof controls | Wrong txid and tampered Merkle branch rejected |
| Historical lookup | Height 800000 linked to the shared historical checkpoint profile |
| Verification switches | Transaction verification and mandatory public-server verification remained enabled |

Machine-readable evidence is generated at `build/reports/purity-mainnet-sync.json`. A previous combined build/network run encountered a handshake-response timeout and a duplicate-version error from the legacy retry path; a fresh, separate final connection passed all validation. No timeout or verification rule was relaxed to obtain the passing result.

The previous mainnet checkpoint blocker is resolved. This acceptance validates headers and transaction inclusion; it does not claim full-node transaction-consensus validation, release signing/publication, physical hardware signing, or native Windows/Linux installation.

### Final local and historical checks
- `JAVA_HOME=/private/tmp/puritywallet-jdk25/Contents/Home ./gradlew :test :desktopTest :jpackageImage --console=plain`: **366 regression tests**, **3 desktop tests**, and the macOS application image passed on the final source.
- `./gradlew :verifyCheckpoint --tests '*HeaderCheckpointVerificationTest.verifyMainnetCheckpoints'`: passed against the shared historical checkpoint profile, separately from the full forward-sync acceptance above.
- `jimage list` confirms the packaged runtime contains `ChainCheckpoints`, `HeaderState`, `PurityHeaderState` and `purity/mainnet-anchor.hex`; the packaged launcher reports `PurityWallet 2.5.6`.
- `git diff --check` passed. drongo and lark source trees remain clean at `13059ebc2979640b30bc1534b4584e2191578672` and `fdcf5a0f0e774bca27a7a08b4e0427465f1dcbac` respectively. No dependency source/commit changes, production deployment, real payment or release publication occurred.

## Base release history merged into PR #2


## Product and chain migration

The source project is Sparrow Wallet 2.5.6. Its actual LICENSE is Apache 2.0; the user confirmed retaining that license.
The inherited defaults used Bitcoin Electrum, Esplora and price services, plus Sparrow's update service. Inapplicable services have been replaced or disabled.

Bitcoin Purity and Bitcoin share their genesis block, address encoding and transaction formats. Genesis verification alone cannot distinguish the fork. Mainnet validation therefore pins activation block 961637 and uses Purity ASERT.

Local parameter sources:

- `bitcoinpurity/src/kernel/chainparams.cpp`
- `bitcoinpurity/src/consensus/params.h`
- `bitcoinpurity/src/pow.cpp`
- `PurityWallet/blue_modules/BlueElectrum.ts`
- `PurityWallet/models/blockExplorer.ts`

Internal package names, hardware-wallet menu references and compatible import identifiers retain their upstream meaning. User-visible product names, installation paths and executables use PurityWallet / puritywallet.
Sparrow wallets and configuration are not migrated automatically, keeping chain data and service settings separate.

Purity Core uses 1.x version encoding (`1.0.0 = 10000`), so the Bitcoin Core `>=240000` version check cannot be reused for it. The mainnet Core adapter validates the Purity subversion and activation block before initializing wallets.

The first migration passed 352 Java tests, one live Purity Electrum checkpoint verification and three Python packaging checks. Its macOS image was built and checked for launcher identity and bundled license files. No dependency source was modified. Windows and Linux configuration received static checks; native builds and execution were not verified.

## Brand artwork and English documentation

Reuse the existing blue-and-white brand assets from the local mobile PurityWallet project. The canonical logo is copied without modifying its artwork. Desktop PNGs are scaled from that transparent master; ICNS and ICO containers wrap these PNGs. The mobile project's opaque native icons are not used, avoiding black rectangles in light themes.
Replace all Sparrow application artwork and route branded JavaFX dialogs and wallet-model views to the PurityWallet PNG. Preserve internal `WalletModel.SPARROW` values for wallet-file compatibility and retain other vendors' artwork.

Translate the requirements intake, product specification, architecture, API contract, migration report and task documents into English. Existing English README, security and reproducible-build documents remain in English.

Asset provenance is recorded in [Brand assets](branding.md). Verification for this follow-up is recorded in [TASK-002](tasks/TASK-002.md).

The artwork follow-up passed 352 Java regression tests and nine Python checks. Light and dark JavaFX renders were visually inspected, including dialogs, wallet-model views, window icons and tray icons. The macOS application and ZIP were rebuilt and checked for the transparent native icon and current runtime resources. Windows and Linux native execution remains unverified.

## Initial PurityWallet release version

The initial PurityWallet release version is `1.0.0b`, replacing the inherited product version `2.5.6`. Upstream attribution still identifies Sparrow Wallet 2.5.6. The application, banners and archive filenames use `1.0.0b`; native metadata uses numeric `1.0.0`. The download verifier retains the beta suffix when locating release manifests and signatures.

## Windows installer validation

[GitHub Actions run 40490528703](https://github.com/PurityWallet/PurityWalletPC/actions/runs/40490528703) built the Windows x64 MSI from commit `e47afe4d7a8441fe3d0ce1300fdba736d1b746e7`. The Windows job passed the Java test task and eleven Python checks, successfully installed the MSI and verified `PurityWallet 1.0.0b` from the installed executable. The downloaded MSI matched its SHA-256 file. See [TASK-004](tasks/TASK-004.md) for the artifact details. This supersedes the earlier Windows validation limitation; Linux native execution remains unverified. The MSI is not code-signed.

## PR #2 merge integration

The merged base release identity, 1.0.0b version, puritywallet launchers, independent data directories, transparent assets and Windows packaging fixes take precedence over the earlier branch-local Sparrow storage identity. The mobile adaptation keeps its node-only fee policy, hidden fiat controls, in-app donation flow and application-selected ChainCheckpoints/PurityHeaderState validation. Earlier validation results above describe their respective pre-merge commits; conflict-resolution verification is recorded separately below.

Verification after merging `origin/master` at `30309168`:

- Java 25 `:test :desktopTest` passed: 372 regression tests and 3 desktop tests, with no failures or skipped tests.
- `python3 src/test/packaging_test.py` passed all 11 packaging checks. No new native installer was built during this merge repair.
- `:verifyCheckpoint --tests '*PurityMainnetSyncTest'` passed at 2026-10-28 06:01:45 UTC: all 9,526 headers from 961632 through 971157 were validated, including the former conflict at 963647. The final hash was `000000000000000015c0b8960bff5722c5cd97c8d18db43eaa0905103a078872`, with accumulated work `2805496392289787167998650`. Cache replay and valid/invalid Merkle-proof controls passed.
- Dependency sources and verification rules were not changed. No release publication or real payment was performed.

## Automatic PR CI

REQ-010 adds regression/desktop test and Windows MSI build/install/version checks for PR updates targeting master and pushes to master. Manual dispatch remains available. CI uses pinned actions, Java 25.0.2 and unchanged dependency sources; Linux desktop tests use Xvfb, while Windows installation validation runs on windows-2022. No signing or release publication is added.

The first manual validation attempt for PR #2 at 03e2a05b was rejected before a run was created: HTTP 422, `Actions has been disabled for this user`. Repository Actions and the workflow were enabled. The same actor had recent runs in the mobile repository with matching repository Actions permissions. This is a dispatch blocker with an unconfirmed upstream cause, not a test failure or successful CI acceptance. See [TASK-008](tasks/TASK-008.md) for current verification status.

The two new workflow checks first failed for the missing PR trigger and missing test workflow, then all 13 Python checks passed after implementation. actionlint 1.7.12 accepted both changed workflows and `git diff --check` passed. These local checks do not substitute for Windows execution or successful GitHub jobs.

Automatic PR CI then passed on implementation revision 7871fd6c: [Tests](https://github.com/PurityWallet/PurityWalletPC/actions/runs/40650559307) ran 372 regression and 3 desktop tests successfully; [Windows Installer](https://github.com/PurityWallet/PurityWalletPC/actions/runs/40650559299) passed 371 regression tests, all 13 Python checks, MSI build and installation, and the installed executable version check (`PurityWallet 1.0.0b`). One pre-existing Windows-inapplicable filesystem-permission test was skipped by its existing platform assumption and passed on Linux. No test failures required application or dependency changes. The successful automatic runs show the manual-dispatch rejection does not prevent PR CI; its underlying cause remains unconfirmed. Artifacts remain unsigned and no release was published.

## Legacy-chain replay warning migration (REQ-011)
The desktop source has no legacy-chain checker. Adapt the mobile wallet's transaction-output/outspend API checks into application-owned Java code and JavaFX views. Preserve normal coin selection, signing and broadcast behavior after explicit acknowledgement; unavailable checks require acknowledgement and must not clear a replay warning. Mainnet only, proxy-aware and disabled offline. Verification is recorded in [TASK-009](tasks/TASK-009.md).

Implemented per-output checks against exact txid/index/value/script and legacy unspent state, a dedicated UTXOs risk column with a recheck action, and a persistent Send warning for the transaction's actual selected inputs. Software/device signing, QR seed signing, raw/imported transaction broadcasting and notification transactions pass a fresh acknowledgement gate. Failures are unknown and require explicit acceptance. Changes during checks or confirmation are discarded; inputs are rechecked for identity after asynchronous wallet decryption. Notification input changes stop the action. The temporary QR seed is cleared after cancellation as well as signing.

Java 25 validation passed 378 regression and 7 isolated JavaFX tests. A separate live read-only API test confirmed that a synthetic transaction's HTTP 404 is classified as absent; output matching and spent-state handling use deterministic fixtures. Light/dark row warnings and the actual Send layout were rendered and inspected; an initially truncated banner was fixed and regression-tested. `git diff --check` passed and dependency sources remain clean. No real signing/broadcast, hardware session, native Windows/Linux UI run, deployment or release publication was performed. Live proxy/Tor behavior was not exercised; the checker uses the existing proxy supplier.

## Release version 1.1.0b — 2026-10-28

The user requested the next application version `1.1.0b`. The application constant and Gradle archive version now agree; macOS bundle metadata uses `1.1.0`, and jpackage continues to strip the beta suffix for native installer metadata. Current specifications and build instructions are synchronized. Prior `1.0.0b` release/CI evidence remains historical. All 13 Python packaging checks and the Java application identity/version regression passed, as did `git diff --check`. Details are recorded in [TASK-010](tasks/TASK-010.md); no release installer was built or published for this change.

## Release version 1.0.0b3 — 2026-10-28

The latest user request supersedes the earlier local `1.1.0b` version. Application and archive versions are now `1.0.0b3`; native metadata uses `1.0.0`. The jpackage version conversion now strips numbered beta suffixes as well as a bare `b`, preventing `b3` from entering numeric installer fields. The existing download verifier already accepts numbered beta versions. Current requirements and documentation are synchronized; prior release evidence remains historical. All 13 Python packaging checks and the Java application version regression passed. A Gradle assertion verified the resolved native package version is `1.0.0`; `git diff --check` passed. Details are recorded in [TASK-010](tasks/TASK-010.md). No release installer was built or published.

## Release version 1.1.0 and product website — 2026-10-31

The latest request sets the release version to `1.1.0` and the product website to `https://bitcoininstitute.com.au`. Application, archive, jpackage and macOS bundle versions now agree; the README shows one version. The former beta release required a separate numeric native version, which is no longer needed. Current requirements and documentation are synchronized, and the GitHub repository About website was updated and verified. Earlier release and build records remain historical. Validation is recorded in [TASK-011](tasks/TASK-011.md); no release installer has been built or published for this change.

## macOS DMG CI — 2026-10-31

The requested CI extension adds a standalone macOS DMG workflow on the same PR, master-push and manual events as Windows validation. Native Apple Silicon and Intel runners execute tests, build the app, apply an ad hoc signature, create a compressed DMG and verify the mounted app before uploading versioned artifacts and checksums. Existing Gradle packaging and dependency sources are unchanged. Apple certificates, notarization and public release publication are outside this workflow. Validation and hosted-run limitations are recorded in [TASK-012](tasks/TASK-012.md).

The user's credential boundary applies to every GitHub workflow: signing private keys, passwords and notarization credentials must not enter repository contents, Secrets, logs or artifacts. The older manual Package workflow's certificate-based signing action and Apple Secrets references were replaced with credential-free ad hoc DMG packaging. Official signing and notarization remain local.

The user authorized committing and pushing this configuration so Windows and macOS installers are generated automatically. Formal signing and notarization are deferred until the Apple Developer account conversion to an organization is complete, with keys and passwords retained locally.

## Versioned signet comparison build — 2026-11-03

REQ-013 identifies the next signet comparison build as 1.1.1 through application, Gradle and macOS version metadata only. Existing version expectations were updated first; the packaging regression failed against 1.1.0 before implementation. Behavior, dependency sources and signing policy are unchanged. Validation and DMG checksums are recorded in [TASK-014](tasks/TASK-014.md). Google review submissions for the original 1.1.0 artifacts remain separate.

Local comparison acceptance passed 13 packaging checks, the Java application version regression, `:jpackageImage`, DMG checksum validation and read-only mounted architecture/version/signature checks. The arm64 DMG SHA-256 is `84b6c2b8771217e99deb18bc2269355bed784792f6069f8495887d22f414065a`. The image was detached. No Chrome verdict, Intel build, commit, push, CI run or release publication is claimed.

## Signet legacy-chain checks — 2026-11-03

The latest REQ-011 extends the mainnet replay checks to Purity signet. Two desktop regressions first reproduced the signet guard bypass and hidden warning. A shared endpoint selector now enables the UTXO column/recheck action, Send preview and signing/broadcast guard for both networks. Mainnet retains `https://mempool.space/api`; signet uses `https://mempool.space/signet/api`, captured before background execution, without mainnet fallback. The existing exact-output and unknown/cancel/stale-result rules remain intact. This behavior change follows the earlier version-only 1.1.1 comparison build; the earlier DMG in TASK-014 does not contain this change. See [TASK-015](tasks/TASK-015.md) for current acceptance.

Signet legacy acceptance passed 388 Java regressions, 7 desktop tests (with replay scenarios exercised on both networks), 13 packaging checks and the tagged live missing-transaction test against both corresponding Bitcoin APIs. Rendered signet UTXO and Send warnings were inspected. Only synthetic/public test data was queried; no transaction was signed or broadcast.

The updated local arm64 package is `build/macos-release/puritywallet-1.1.1-signet-legacy-macos-arm64.dmg`, SHA-256 `35317568df8093da2b2ba2a8dc5d4f9be03f147838e8cd49481d25efa06bc93d`. Embedded legacy-checker bytecode matches the tested class. The DMG passed checksum, mounted version/architecture/shortcut and deep/strict ad hoc signature checks, then was detached. This is local acceptance only; no commit, push, hosted CI, Intel build or Chrome verdict is claimed.

## Upstream Sparrow comparison — 2026-11-28 (REQ-018)

The fork point is upstream `d61b2a6` (2026-10-26). Upstream `sparrowwallet/sparrow` master at `114389b` (2026-11-26) has 185 further commits. Its drongo pin `aa75a9b` is 25 commits ahead of the locked `13059eb`, and its lark pin `631ddbe` is 6 commits ahead of `fdcf5a0`. 27 of the Sparrow commits also move the drongo or lark pin.

### Method
All checks ran in a temporary worktree; no project branch was changed.

- Every upstream commit was cherry-picked in order onto `1467d26`, taking the upstream drongo or lark pin where a commit moves one. 147 commits apply cleanly. 38 conflict, nearly all in a single hunk: rebranded strings, the removed fiat and update-check code, build files, the fork's own CI, and commits that depend on an earlier conflicting one.
- With four small adjustments the result compiled with Temurin 25.0.2 under strict dependency verification. The adjustments were the pgpainless 1.7.8 checksums from `62b2f7e` (needed by the newer drongo), a one-hunk merge of `a7ccc7d` (needed by `539fbfc`), the `DecorativePane` class from `66fba50`, and leaving out `9b566c1`, which needs the unit formatting of `26765f2`.
- `:test` then ran 547 tests with no failures and 7 platform skips. `DbPersistenceTest.walletNamePunctuation`, added by upstream `2bf2830`, needs a UTF-8 locale and passes with one.
- Desktop tests, live checkpoint checks and packaging were not run on that worktree.
- A trial `git merge upstream/master` conflicts in 23 files, about 35 hunks.

### Findings
- **The drongo upgrade carries many of the most important fixes**: silent payment inputs must be signed with SIGHASH_ALL (`1162ab4`), BIP 353 lookups require every DNS response to be authenticated (`90d9385`), malformed or non-standard descriptors are refused (`d81ff1a`, `2cf12e7`), the public copy of a PSBT drops redeem and witness scripts (`3378d61`), and parsing errors no longer repeat key or descriptor text (`18516bb`). The Sparrow commits that use them (`418e726`, `89e1a37`, `3d2fbac`, `6a00016`, `9b998a7`, `0aaec9c`, `08f3251`, `c1fbc8a`, `7713221`, `98173c6`) need the new pin. Moving the pin changes no dependency source but replaces the locked revision, so it needs a separate decision.
- **Wallet file and key handling** (mostly clean): imported Sparrow wallet files have their addresses derived again instead of trusted (`0b0e0e1`); an xprv is refused in place of an xpub (`97742ca`); account keystores are encrypted when a password is set with a refresh (`4acaaca`) and stay encrypted during account discovery (`0f9d99f`); backups made before a password was added or changed are found and removed (`abb1c2c`, `97ed5bb`, `8f4b70e`, `26e6705`, `a50cb06`, `ddb486d`, `e7ad60b`); the opened wallet file is the validated one (`389ac27`); locked wallets are excluded from auth47, LNURL-auth and `bitcoin:` URI wallet selection (`4447969`); a PayNym directory cannot get an auth47 login signed as an authentication token (`ebd3bda`).
- **Privacy and network**: a proxy that reports it could not reach the server no longer turns the proxy off (`ebe8346`), and onion hosts written with a trailing dot still go over Tor (`70f2f51`). While internal Tor is starting, auth47 and LNURL-auth logins (`71cd1c7`), payjoin requests (`59e880d`) and BIP 353 lookups (`58010d5`) are held back. `71cd1c7` and `58010d5` conflict where the fork removed rates and version checks.
- **Server robustness**: proofs are checked again after reorgs (`ca2be7f`, `40310b0`, `f735790`, `9673591`, `40724fa`); the gap limit is extended only for addresses that a fetched transaction pays (`466b424`); oversized responses (`a108ad1`), transactions (`485eddb`) and BBQR payloads (`8b1aaf5`) are bounded; the log is capped at 10 MB (`ebf0484`). The reorg commits touch `ElectrumServer`, where Purity header validation lives. They apply and pass unit tests, but need `:verifyCheckpoint` on mainnet and signet before adoption.
- **Bundled runtime**: the fork ships Temurin 25.0.2 (TASK-022 updates it to 25.0.5.1+2), Tor 0.4.8.21, JavaFX 26 and Jackson 2.21.1. Upstream `21b4032` moves Tor to 0.4.9.5 to meet the client protocols the Tor network requires.
- **Tor publisher**: upstream `ab2f00e` takes Tor 0.4.9.16 from resources republished under `com.sparrowwallet`, not from the kmp-tor project's `io.matthewnelson.kmp-tor` artifacts, whose latest release is 409.5.0. Adopting it would change who publishes the bundled Tor binaries.
- **Data formats**: `485eddb` adds wallet database migration V11, and `2529bd7` upgrades Flyway 9.22.3 → 13.12.1. Flyway ignores future migrations by default, so older releases still open migrated wallets; both still need wallet open and migration checks.
- **Not applicable**: upstream version bumps (`22655a1`, `72182ee`), CoinGecko rates (`646a11d`), testnet5 and libbitcoin servers (`c21a458`, `77b1b50`), and upstream documentation and housekeeping (`6a83c9a`, `1ebbc57`).

### Batches
| Batch | Content | Notes |
|---|---|---|
| 1 | Tor 0.4.9.5, JavaFX 27.0.1 with its regression fixes, Jackson 2.21.8 | TASK-023; the JDK is TASK-022 |
| 2 | drongo and lark pins with the Sparrow commits that need them | Needs the pin decision |
| 3 | Wallet file and key handling | Mostly clean |
| 4 | Privacy and network | `71cd1c7` and `58010d5` need adaptation |
| 5 | Server robustness and SPV | Requires `:verifyCheckpoint`; `ebf0484` keeps `puritywallet.log` |
| 6 | Other fixes and accessibility | Accessibility commits depend on each other; bring them in order |

### Upstream commits
Each subject is upstream's. "Cherry-pick" shows the result of the in-order simulation described above.

#### Keys and funds (42)

| Commit | Upstream subject | Cherry-pick in order onto `1467d26` |
|---|---|---|
| `abb1c2c` | only delete wallet backups after a password change or addition has saved, and hide the backup options for wallets outside the wallets folder | clean |
| `e7ad60b` | keep a symlinked wallet file in place when adding, changing or removing its password | clean |
| `d156f00` | keep the master private key for xprv output descriptors with an account path, and only warn of conversion to watch only when the xprv has a key origin path (moves drongo pin) | conflict: `SettingsController.java` |
| `0b0e0e1` | derive node addresses rather than using stored addresses when importing a sparrow wallet file (moves drongo pin) | clean |
| `e31af45` | sum the swept key once per input when sweeping to a silent payment address, and refuse silent payment sweeps from uncompressed keys | clean |
| `2bf2830` | apply the wallet name dialog character filter to names suggested by imported wallet files | clean |
| `a50cb06` | delete backups made before a wallet was migrated from json when deleting a wallet or its backups | clean |
| `ddb486d` | rename wallet backups along with the wallet when renaming | clean |
| `51d01d9` | clear a selected paynym from the send tab pay to field before applying a scanned address or uri | clean |
| `97ed5bb` | report a wallet password change that fails on the update thread, and delete backups only once the file has been written with the new password | clean |
| `f4d53da` | escape the wallet name when using it to name the database update thread | clean |
| `0aaec9c` | include the non-witness utxo in psbts for jade keystores, and in the qr display when the psbt has more than one input (moves drongo pin) | clean |
| `389ac27` | open the wallet file that was validated, rejecting a wallet file with an empty name (moves drongo pin) | clean |
| `689f9fb` | import a descriptor wrapped over several lines or followed by other text in a pdf, and refuse ec or duplicate keys in a scanned multisig (moves drongo pin) | clean |
| `7df6f35` | parse a multisig setup file line by line on newlines only, with case-insensitive labels, a strict policy value and no repeated policy or format | clean |
| `7713221` | keep the entered address for a payment when showing its cached payment instruction name in the send tab and send to many (moves drongo pin) | clean |
| `418e726` | apply the required all sighash to the inputs of a psbt with silent payment outputs in the transaction tab, and check each input a card will sign before it signs any (moves drongo pin) | clean |
| `1f5993b` | disable the locktime controls for a psbt input requiring a locktime, and show the locktime that will be signed | clean |
| `2443af4` | list payments to other addresses in the summary of a transaction paying premix outputs | clean |
| `a7ccc7d` | verify silent payment outputs against the open wallet providing all inputs when a psbt is exported or combined before finalizing | conflict: `AppServices.java` |
| `44e7ee9` | give the transaction tab a copy of a wallet transaction, and leave locktimes and input sequences unchanged as the tab opens | clean |
| `4d3acfe` | remove control and format characters from paynym names, and omit directory entries whose payment code has no valid key | clean |
| `ebd3bda` | refuse to sign a paynym authentication token in another format | clean |
| `f48f577` | keep stored and followed paynym contacts over directory entries and their names, search the listed contacts, and show the payment code when linking | clean |
| `9b566c1` | ask before broadcasting a paynym link transaction at a very high fee rate | clean |
| `37ea7b9` | remove auth47 soroban callback support | clean |
| `26e6705` | delete backups made before a password was added or changed from the terminal settings | clean |
| `8f4b70e` | find, rename and delete the backups of a wallet left in a previous wallets folder when its password is added, it is renamed or it is deleted | clean |
| `4acaaca` | encrypt account keystores when a password is set together with a change needing a refresh, and edit an account as a child of the master wallet copy | clean |
| `0f9d99f` | keep the open wallet encrypted while discovering accounts to add | clean |
| `c1d4fee` | replace control characters in cosigner labels of an imported multisig configuration, and size alerts to include blank lines | clean |
| `97742ca` | refuse an extended private key in place of an xpub in imported wallet files, and use the public key of a private hdkey in a scanned output descriptor | clean |
| `002b1b5` | use the bip352 derivation for silent payment wallets imported from a mnemonic or device, created in the terminal, scanned from a seedqr or added at another account (moves drongo pin) | clean |
| `3d2fbac` | refuse descriptors that do not derive on the standard receive and change chains when importing a wallet or keystore, and warn of them in the descriptor editor (moves drongo pin) | conflict: `SettingsController.java` |
| `6a00016` | apply the child derivations of keys in scanned output descriptors, and refuse those that do not derive on the standard chains | clean |
| `0d4f148` | decide whether a border wallets grid is hex from all of its cells rather than the selected ones | clean |
| `9b998a7` | keep the encrypted master secret of imported slip39 shares, so that the wallet decrypts it with any passphrase as the shares do (moves drongo pin) | clean |
| `08f3251` | warn that a bip353 name resolved to a silent payment address for a different network on the send tab and in send to many, rather than reporting it has no address (moves drongo pin) | clean |
| `c1fbc8a` | warn that a bip353 record placed a silent payment address under the wrong network parameter on the send tab and in send to many (moves drongo pin) | clean |
| `89e1a37` | refuse silent payment outputs in a psbt with an input spending a segwit version 2 or higher output (moves drongo pin) | clean |
| `539fbfc` | warn when opening a psbt whose silent payment outputs cannot be verified | clean |
| `4447969` | exclude locked wallets from the wallet selection for auth47, lnurl-auth and bitcoin uris | clean |

#### Privacy, network and TLS (20)

| Commit | Upstream subject | Cherry-pick in order onto `1467d26` |
|---|---|---|
| `4afb794` | keep an entered https or ssl when the host entered is also the alias of a saved server at another protocol | clean |
| `de36acf` | keep the saved server or proxy when an invalid port is entered in server settings, instead of connecting to the default port | clean |
| `a1b99e3` | prompt to accept an expired server certificate in settings test connection, and say an expired saved certificate has expired rather than changed | clean |
| `9eb7519` | save a bitcoin core server chosen or scanned that differs only in protocol | clean |
| `5551284` | stop the external proxy status check while offline, and restart it on going online | clean |
| `ebe8346` | treat a proxy's reply that it could not reach the server as a server failure, so it no longer turns off the proxy | clean |
| `71cd1c7` | hold rates, version and currency requests and refuse auth47 and lnurl-auth logins while internal tor is starting | conflict: `AppServices.java`, `GeneralSettingsController.java` |
| `7b6e844` | create the server transport only when connecting, so a call made after disconnecting cannot leave one for the next connection | clean |
| `70f2f51` | reach an onion host written with a trailing dot over tor, and remove the dot from saved server urls | clean |
| `921c43b` | use the default port for a proxy given without one in http requests, bwt, tor identity changes and tor status checks | clean |
| `63e1993` | accept a proxy port given with the host, and keep the saved proxy when the host is invalid, in the desktop and terminal settings | clean |
| `6404bb4` | stop a connection waiting to retry while the server settings are open, and reconnect on closing | clean |
| `02f8a0c` | check whether a server host is an ip literal as inetaddress reads one | conflict: `ServerSettingsController.java` |
| `b935568` | refuse the bwt fallback for bitcoin core over https and ignore the legacy wallet setting for it | clean |
| `1071ed7` | check rsa server certificates against the ca trust store for the key exchange in use | clean |
| `6ea7bca` | show the remote bitcoin core node warning from the terminal server settings | conflict: `ServerSettingsController.java` |
| `e2cc144` | upgrade tern to v1.1.4 to use an empty cookie store in the http client (moves lark pin) | conflict: `build.gradle`, `verification-metadata.xml` |
| `91a00d6` | offer to replace a saved server certificate when the new certificate has the same subject, as it fails path validation rather than path building | clean |
| `59e880d` | refuse to request a payjoin transaction while waiting for internal tor to start | conflict: `Payjoin.java` |
| `58010d5` | resolve a bip 353 name entered while offline or while tor is starting once connected, and unregister payment controllers when their tab or wallet closes | conflict: `AppServices.java` |

#### Server robustness and SPV (27)

| Commit | Upstream subject | Cherry-pick in order onto `1467d26` |
|---|---|---|
| `ca2be7f` | show a transaction tab confirmation as unverified when the block the shown transaction carries has been replaced by a reorg | clean |
| `87ff0f7` | stop a silent payments wallet scan that completes after its wallet is closed from refreshing and subscribing the closed wallet again | clean |
| `9ab8e0b` | stop labelling a heavily reused address from freezing the ui, and commit receive tab labels on enter so the whole label propagates (moves drongo pin) | clean |
| `f2ecfb2` | resend batch requests that have been skipped as making the batch response too large, so a large wallet history page syncs instead of failing every refresh | conflict: `ElectrumServerRpc.java` |
| `a108ad1` | keep the start of a server response that arrives across socket read timeouts instead of dropping it, and fail the connection on a response over 256m characters | clean |
| `fb0e585` | order block transaction references consistently for any height (moves drongo pin) | clean |
| `0615085` | skip a silent payments transaction the server cannot return when processing a subscription batch, and fetch it again on the next refresh | clean |
| `9f29f57` | save silent payments found by a scan when fetching their history fails, and load wallets whose silent payment indexes are not contiguous | clean |
| `c430f8a` | keep silent payment addresses recorded or cleared while the settings tab is open when saving wallet settings (moves drongo pin) | clean |
| `485eddb` | widen stored transactions to the consensus maximum size and skip larger ones fetched from the server | clean |
| `efee1ba` | restart the stale tip warning clock only when the server announces a block it has not announced before | conflict: `ElectrumServer.java` |
| `4151ee9` | keep the results of every other page when one page of a batched history, mempool, transaction, block header or block stats request returns an error | clean |
| `9673591` | mark a transaction tab height unverified when its proof cannot be obtained from a connected server, and fetch it again after the server changes | clean |
| `6e42959` | keep a master wallet entry for a transaction otherwise held only by a paynym wallet, so history continues to sync | clean |
| `98173c6` | omit bound values from wallet database error messages, and the entered text from the terminal descriptor import log (moves drongo pin) | clean |
| `40724fa` | compare a re-announced chain tip by block when deciding whether to retry or refuse it | clean |
| `466b424` | extend the gap limit only for addresses paid by a fetched transaction | clean |
| `ebf0484` | cap sparrow.log at 10mb with one rolled backup, and ignore server notifications that match no subscription | conflict: `logback.xml` |
| `9779b3e` | validate every schema in a wallet file in one pass before and after migration, rather than each child wallet schema twice | clean |
| `6bcd4f2` | keep the label and status of outputs at an address the server briefly reports without history (moves drongo pin) | clean |
| `40310b0` | sync to the announced tip before serving a stored header when a reorg lengthens the chain, so a proof is not checked against a replaced block | clean |
| `0721ca9` | truncate address, purpose node and keystore labels when updating a wallet, as a full save does | clean |
| `5c47c3a` | save the freeze status and labels of nested wallet outputs to the wallet that stores them | clean |
| `f735790` | check a proof that does not reconstruct against the server chain before reporting it, as a server can report a transaction on a branch before announcing its tip | clean |
| `96d217c` | wait for a slow electrum server response instead of resending the request, halving batch page sizes only after a request goes unanswered, and retry the keepalive ping once | clean |
| `4df1831` | skip the keepalive ping while another request is awaiting its response | clean |
| `8b1aaf5` | cap the size of decompressed bbqr zlib data when scanning qr codes | clean |

#### Dependencies and runtime (20)

| Commit | Upstream subject | Cherry-pick in order onto `1467d26` |
|---|---|---|
| `d8e679d` | upgrade to java 25.0.5 | conflict: `README.md`, `reproducible.md` |
| `8ea8e30` | upgrade to javafx 27.0.1 | clean |
| `f13a4d9` | fix camera preview in the qr scan dialog disappearing after the first frame with javafx 27 | clean |
| `999a823` | upgrade to jackson 2.21.8 (moves lark pin) | clean |
| `e840d47` | update dependency verification metadata for jackson 2.21.8 | clean |
| `c7c8409` | remove stale jackson 2.21.1 dependency verification entries | clean |
| `2ce667d` | add missing jackson 2.21.8 parent pom dependency verification entries | clean |
| `21b4032` | upgrade bundled tor to 0.4.9.5 to meet the network required client protocols | clean |
| `d0b648d` | raise the javafx texture size limit on macos so main windows over 4096 pixels wide render with the metal pipeline instead of staying blank | conflict: `build.gradle` |
| `7d93ed2` | upgrade jzbar to v0.5.0 and load its renamed libiconv-2.dll on windows | conflict: `build.gradle` |
| `2529bd7` | upgrade flyway to v13.12.1 | clean |
| `62b2f7e` | upgrade pgpainless to v1.7.8 (moves drongo pin) | conflict: `verification-metadata.xml` |
| `96c3ef6` | upgrade tern to v1.1.2 and use it for bitcoin core rpc and paynym avatar requests (moves lark pin) | conflict: `verification-metadata.xml` |
| `1356b2c` | request bitcoin core wallet spends in batches from v28 when starting cormorant and wait for the initial load before polling | conflict: `BitcoindTransport.java`, `BitcoindTransportProxyTest.java` |
| `3ca7c4b` | add jetty 12.1.15 verification checksums and remove stale entries and signing keys left by earlier upgrades | conflict: `verification-metadata.xml` |
| `ab2f00e` | upgrade tor to 0.4.9.16 using the com.sparrowwallet tor resources | conflict: `build.gradle`, `verification-metadata.xml` |
| `5cc8e9b` | upgrade tern to v1.1.3 to point to rpcallowip when bitcoin core closes the rpc connection as the request is sent (moves lark pin) | conflict: `build.gradle`, `verification-metadata.xml` |
| `806358e` | switch the code.sparrowwallet.com dependencies to maven central, moving bwt-jni, fxsvgimage and bokmakierie to com.sparrowwallet (moves lark pin) | conflict: `build.gradle`, `verification-metadata.xml` |
| `8cdfab3` | upgrade to java 25.0.5.1+2 and pin the exact build in ci | conflict: `package.yaml`, `.sdkmanrc`, `README.md`, `reproducible.md` |
| `114389b` | restore the file extension when saving or exporting files on macos, which the save dialog has dropped since javafx 27 | clean |

#### Fixes and features (26)

| Commit | Upstream subject | Cherry-pick in order onto `1467d26` |
|---|---|---|
| `c931bfb` | point to rpcallowip when bitcoin core v32.1+ closes the rpc connection, and read the mempool vsize from vsize_adjusted | clean |
| `1c48829` | point to rpcallowip when bitcoin core resets the rpc connection before the connect completes, not only once the request has been sent | clean |
| `a7d6733` | show the wrong network error when a coldcard multisig keystore import fails, instead of the singlesig fallback derivation not found error | clean |
| `2e9e2d2` | stop the send tab transaction preview from opening a cleared transaction in the expanded popup | clean |
| `f9bf2c4` | avoid creating a node entry for every utxo table address cell update | clean |
| `ac1ea56` | follow up to address context menu change | clean |
| `26765f2` | add 1 234,56 unit format and use the unit format rather than the system locale for fee rates, sizes, percentages and chart labels (moves drongo/lark pin) | conflict: `GeneralDialog.java` |
| `34413b2` | use the latest mempool data in the block summary service, and reset it on reconnecting | clean |
| `4aed804` | name the device and its fingerprint when a connected device does not match the wallet keystores | clean |
| `8717f31` | accept 64 byte sighash default taproot signatures when verifying bip322 messages (moves drongo pin) | clean |
| `46684fe` | add test coverage for multisig ur output descriptor exports | clean |
| `2971002` | switch the send amount and fee fields to btc when a btc amount below 1 is pasted into them in sats, and restore the default unit when the form is cleared | conflict: `CoinTextFormatter.java` |
| `5242c52` | omit the copy value actions from the transaction diagram context menus when amounts are hidden | clean |
| `d1a2266` | apply hide amounts to the transaction hex on the input and output views in the light theme, and to unselected output values when an output is selected | clean |
| `e36312e` | freeze and unfreeze the row whose menu was opened from the keyboard when it is not in the selection, and leave spent outputs in a selection unchanged | clean |
| `53c2e16` | report the card status when a satochip extended key request does not succeed instead of parsing the response | clean |
| `1f3c2c5` | select the saved camera by its id before falling back to its name, so that a camera with the same name is not opened in its place | clean |
| `6a1520c` | avoid an error labelling silent payment outputs from an open transaction tab before the wallet has fetched their transaction | clean |
| `3ef0d30` | open the device signing dialog for a card keystore in a multisig wallet that also holds seed and watch only keystores | clean |
| `5316f48` | wrap terminal alert lines after a line break in the text at the full width | clean |
| `a18db11` | let the terminal user choose the script type when transactions are found for more than one | clean |
| `0c28eaf` | leave qr codes that zbar can only return as transcoded text to the other scanners | clean |
| `868fa08` | keep the monospaced font of utxo addresses when the table is refreshed | clean |
| `86e5231` | reduce the minimum window width to 750, fitting the send tab, transaction diagram and transaction tab views to narrow windows and narrowing the wallet menu and transaction tree below 900 | conflict: `AppServices.java` |
| `f044870` | add single signature output descriptor coverage and generalize test helpers | clean |
| `5b823db` | keep the send tab payment fields in place when a second payment is added or closed | clean |

#### Accessibility (43)

| Commit | Upstream subject | Cherry-pick in order onto `1467d26` |
|---|---|---|
| `fdd5c2a` | hide inactive wallet and transaction views so only the visible view is focusable | clean |
| `0b23f27` | expose toggle switches to screen readers with their state and a name taken from the enclosing form field | clean |
| `5f3feaa` | replace chart data points with a one-line summary for screen readers on the balance, utxo and fee rate charts | clean |
| `8548aef` | hide the no-wallets hint while tabs are open so screen readers skip it, and fix its drag-over outline | clean |
| `28fd2c9` | expose single-choice toggle groups to screen readers as radio buttons instead of checkboxes | conflict: `server.fxml` |
| `bd8c9a7` | keep a selection in single-choice toggle groups when the selected button is pressed again | clean |
| `9545cd5` | give wallet and transaction tabs their name as tab text so screen readers can read it | clean |
| `de2b547` | let screen readers reach dialog contents on macOS and give the settings dialog a title | clean |
| `572cd12` | expose action buttons built from toggle buttons to screen readers as buttons instead of checkboxes | clean |
| `a08f5ff` | give icon-only buttons accessible names so screen readers can announce them | clean |
| `0b8f237` | expose help icon text to screen readers instead of an empty glyph | clean |
| `10044a1` | report fee rates from the fee slider, name the blocks slider and receive QR codes, and stop exposing address chunks to screen readers | clean |
| `641d87e` | hide empty transaction tree rows from screen readers | clean |
| `dab98e6` | hide empty wallet table rows from screen readers | clean |
| `2fb12be` | hide empty server alias and paynym list rows from screen readers | clean |
| `a2c88fd` | improve voiceover reading of the send form | clean |
| `63bc206` | improve keyboard and screen reader access to seed words, dark theme buttons, text areas, keystore sources and the cosigners slider | clean |
| `cdadd76` | read signatures as a summary for screen readers, and move focus to the signatures after signing and to the transaction status after broadcasting | clean |
| `dfa9782` | keep the recent blocks summary current for screen readers | clean |
| `5b18668` | name the passphrase checkbox and field, and the derivation fields in the import panes, for screen readers | clean |
| `71dc95d` | tell screen readers when the selected tab changes so only the open tab reads as selected | clean |
| `aaa0293` | name split buttons from their text so screen readers read them | clean |
| `ccb6627` | name the pay to wallet chooser and let the keyboard browse it before choosing | clean |
| `cd56ab7` | give dialogs titles and keep focus on the new tab or button after creating a transaction or keystore | clean |
| `d4cae70` | remove unnamed and hidden tab stops and expose code areas to screen readers | clean |
| `7d43eb0` | focus new wallet tabs and announce the cosigners slider counts as they change | clean |
| `057ff65` | browse the server and sweep lists before choosing, keep the transaction status current for screen readers, and name alert icons | clean |
| `66fba50` | hide decorative images from screen readers, announce the first list item, start the import dialog on its source and keep the list button in step | conflict: `DialogImage.java`, `WalletModelImage.java` |
| `4c275f6` | keep chart points out of the tab order, let the message dialog close after signing, and name the qr code, encoding and message dialog | conflict: `MessageSignDialog.java` |
| `de4b421` | let escape close dialogs from split buttons, name the port fields and read out the test connection result | conflict: `AppServices.java` |
| `e5c0cb1` | take row buttons out of the tab order and open row actions from the keyboard with shift-f10 or control-return | clean |
| `ea39b09` | focus test connection after editing it, keep one tab stop for the message format and name each exclude utxo button | conflict: `MessageSignDialog.java` |
| `20c1aea` | focus the first device found when scanning in the import wallet dialog, report when none are found and read the status for each pane | clean |
| `4f5f5ee` | name the paynym dialog fields, lists, contact rows and buttons, and hide the avatars from screen readers | clean |
| `2c4c372` | title the verify download dialog, name its browse buttons and results, move focus to the results and let escape close it | conflict: `DownloadVerifierDialog.java` |
| `96a82ff` | name the wallet name field for screen readers | clean |
| `04cd941` | name the connected devices button, keep focus on the device in device dialogs and read device and card errors as they appear | clean |
| `dd4b0ad` | fix the send to many keyboard trap, close idle tables with escape, and name the search, scan and sweep dialog controls | conflict: `AppServices.java`, `SparrowDesktop.java`, `SearchWalletDialog.java` |
| `32d30fb` | make the transaction status a tab stop, name the fee and locktime fields in the transaction tab, and name the notification close button | conflict: `headers.fxml`, `input.fxml` |
| `66b3f71` | read whole rows in the search wallet results, name the add account pop-up and hide the glyph icons in dialog headers from screen readers | clean |
| `67b51a4` | add a view > go to menu with shortcut+1 to 6 to select the wallet function and focus its menu button | clean |
| `21ca0a4` | read whole rows in the wallet summary table, sharing the row summary with the search wallet results | clean |
| `830c286` | handle shift-f10 and the menu key in tables on linux, since javafx only turns them into a context menu request on windows | clean |

#### Not applicable (7)

| Commit | Upstream subject | Cherry-pick in order onto `1467d26` |
|---|---|---|
| `22655a1` | bump to v2.5.7 | conflict: `build.gradle`, `reproducible.md`, `Info.plist`, `SparrowWallet.java` |
| `1ebbc57` | tidy repository housekeeping files | clean |
| `c21a458` | add testnet5 network support and libbitcoin public electrum servers (moves drongo pin) | conflict: `README.md`, `AppController.java`, `PublicElectrumServer.java`, `HeaderCheckpointVerificationTest.java` |
| `646a11d` | request historical rates from coingecko in whole utc days | conflict: `ExchangeSource.java` |
| `6a83c9a` | document building v2.5.3 to v2.5.6 with lenient dependency verification, as two pom files were later republished to maven central with additional metadata | clean |
| `77b1b50` | add libbitcoin to detected electrum server capabilities list | clean |
| `72182ee` | bump to v2.5.8 | conflict: `build.gradle`, `reproducible.md`, `Info.plist`, `SparrowWallet.java` |
