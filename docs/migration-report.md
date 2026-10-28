# Migration report

## Scope
Mobile REQ-001/002/003 are adapted; REQ-004 is satisfied; REQ-005/007 are platform-specific and excluded; REQ-006 retains full desktop behavior; REQ-008 retains desktop notifications without mobile push.

## Compatibility
Preserve application identities, storage paths, packages, serialized formats, user custom servers/explorers, wallet imports, LNURL-auth and desktop signing capabilities. Disable external fees, fiat and upstream update checks even with old saved settings. No data is migrated, deleted or rewritten by a migration. The fork does not promise independent coexistence with Sparrow.

## Verification and release boundary
Use Java 25 and pinned submodules; never modify dependency sources. Run regression tests, local build/launch and read-only Purity RPC/history checks. Checkpoint or chain incompatibility blocks live acceptance; do not bypass validation. Signing, notarization, publication and real payments are excluded. Results will be recorded below after verification.

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

### Blocking live acceptance
`./gradlew :verifyCheckpoint --tests '*HeaderCheckpointVerificationTest.verifyMainnetCheckpoints'` fails at the pinned height **963647**:

| Source | Block hash |
|---|---|
| Pinned upstream dependency | `00000000000000000000d29ee44c13e924c37abcaa80c537609ce70b1fea1f33` |
| Purity Electrum | `000000000000003e346d906ea62abdf577ede019541e82796f81e243ff907626` |

This is a chain compatibility failure, not a TLS or RPC outage. Full header synchronization and wallet-history acceptance cannot pass with these checkpoints. No dependency source was edited and no verification was disabled. Resolving chain/checkpoint compatibility requires a separately authorized dependency or chain-validation change before production use.

Windows/Linux installation and execution have not been tested natively on this macOS host; their metadata and assets were checked statically. Hardware tests cover wallet selection and PSBT routing, not a physical device signing session. No real transaction, release signing, notarization, publication or deployment occurred.

### Packaged macOS application
`./gradlew :test :desktopTest :jpackageImage` completed successfully. The bundle is `build/jpackage/PurityWallet.app`; its `CFBundleName`, executable and icon resolve correctly, while `CFBundleIdentifier` remains `com.sparrowwallet.sparrow`. Its bundled runtime reports `PurityWallet 2.5.6` for `--version`.

The packaged application was launched using an isolated temporary home, `mode=OFFLINE`, `loadRecentWallets=false` and `SPARROW_NO_LOCK_FILE_LINK=true`. The process remained running and was then terminated by its exact temporary-home command-line match. No existing wallet instance was operated. Native UI automation matched another instance sharing the Bundle ID and was not used as evidence; the separate JavaFX rendering/interaction tests provide UI verification.

The build emits existing JEP 493/modular-JAR and module-name warnings. No release signing identity or notarization was used; the existing local launcher patch applies only an ad hoc development signature.
