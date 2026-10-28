# Migration report

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
