# External API Contract

- Product website: https://bitcoininstitute.com.au.
- Electrum JSON-RPC over TLS: `electrum.bitcoinpurity.org:50002`; client name `PurityWallet`.
- Purity signet Electrum JSON-RPC over TLS: `electrum.bitcoinpurity.org:60002`; client name `PurityWallet`. No fallback to original Bitcoin signet peers.
- Purity signet explorer: https://mempool.bitcoinpurity.org/signet; transaction links use `/signet/tx/{txid}` exactly once. Custom explorer templates and disabled exploration remain available.
- Bitcoin Purity Core: compatible with Bitcoin Core RPC. The user selects the node; the pinned Purity activation block validates chain identity before wallet initialization.
- Purity signet chain identity: activation height 327022, hash `00000004b42cf68f94be9dd89b65e44cae31eee382137221b83b5b261d3e3c20`; shared parent 327021, hash `00000013c351c0d13fc2c8b3547413b3ed30854a2434869c064a5076bab6aad6`. ASERT anchor 327017 uses bits `1d154f9b`, parent time 1792401042, spacing 600 seconds and half-life 86400 seconds. Wallet serialization and dependency revisions are unchanged.
- Esplora REST: https://mempool.bitcoinpurity.org/api/ for block data and fallback transaction broadcasting; automatic fees use the connected node.
- Preserve the protocol names for `bitcoin:` payment URIs, PSBT and compatible wallet import formats.
- Do not use Sparrow's update-signing identity or Bitcoin market-price services as a source of Purity prices.

Brand assets and documentation language changes do not change these external interfaces.

The application reports version `1.1.1`, including in its local Electrum adapter banner. Native installer metadata uses the same version `1.1.1`.

Automatic PR CI does not change runtime APIs, wallet formats or dependency revisions. Its GitHub token has contents:read permission only. Workflows that resolve dependencies also pass the read-only repository secret `PWC_PACKAGES_TOKEN` (with the actor as user name) to Gradle, at step level, to download the PWC SDK from GitHub Packages; it is not a signing or notarization credential. Pull requests from forks do not receive it and cannot resolve the SDK. Test reports, JaCoCo coverage reports of the `:test` suite and unsigned Windows installers with SHA-256 files are CI artifacts, not published releases; coverage data is not sent to any external service. The live network checkpoint task remains a separate, explicitly invoked acceptance check.

macOS CI adds a `PurityWallet-<version>-macos-arm64` artifact containing a DMG and a SHA-256 file. The app is ad hoc signed without notarization. The workflow has contents:read permission and no Apple secrets; runtime interfaces remain unchanged.

All GitHub packaging workflows must operate without signing private keys, certificates containing private keys, passwords or notarization credentials. Those credentials must not be stored in GitHub repositories or Secrets, printed in logs, or included in artifacts. Official signing and notarization are local operations.

## Legacy-chain checks (REQ-011)
Mainnet uses read-only `GET https://mempool.space/api/tx/{txid}` and `/tx/{txid}/outspends`; Purity signet uses the corresponding paths under `https://mempool.space/signet/api`. The service captures the network-specific base URL when creating its task, with no cross-network fallback. The transaction must identify the requested txid; each checked vout must supply an integer value, hexadecimal scriptpubkey and boolean spent state. Only transaction HTTP 404 means absent; timeouts, other HTTP failures, missing metadata and malformed bodies mean unknown. Requests use the configured proxy, a 10-second request timeout, at most four concurrent transaction groups and no persistent response cache. Only txids are sent; no addresses, keys, PSBTs or signed transactions are submitted. Offline mode, regtest and unsupported testnets make no legacy requests. This service provides advisory replay information and does not replace Purity verification.

## PWC LOCAL provider (REQ-014)
- Protocol: PWC Core v0.2.1 over LOCAL v0.1.0 (`pwc-local/1`), implemented by `org.bitcoinpurity.pwc:pwc-provider-java:0.2.1` from `https://maven.pkg.github.com/BitcoinPurity/pwc-spec`. Capabilities, limits, method semantics and error codes are those of the SDK's API contract.
- Provider id and endpoint: `puritywallet`, `puritywallet-signet` or `puritywallet-regtest`, giving `<endpoint directory>/<id>.sock` on macOS and Linux and `\\.\pipe\pwc-<sid>-<id>` on Windows.
- Account methods:

| Account | Methods beyond reads and addresses |
|---|---|
| Software single-sig P2WPKH | `psbt.create`, `psbt.releaseReservation`, `psbt.sign`, `message.sign` (`bip322-simple`) |
| Software single-sig P2PKH | `message.sign` (`legacy`) |
| USB hardware single-sig P2WPKH | `psbt.create`, `psbt.releaseReservation`, `psbt.sign` |
| Watch-only or airgapped single-sig P2WPKH | `psbt.create`, `psbt.releaseReservation` |
| Watch-only or airgapped single-sig P2PKH | None (`can_sign: false`) |

Multisignature, nested SegWit, Taproot and silent-payment accounts are not listed: the SDK `AccountInfo.scriptType` has only `P2WPKH` and `P2PKH`. A wallet with no listable account is not listed.

- Backend errors: unknown or closed account `ACCOUNT_NOT_FOUND`; locked wallet `WALLET_LOCKED`; history never loaded `SYNC_INCOMPLETE`; no fee estimate (offline) none returned; insufficient funds `INSUFFICIENT_FUNDS`; an encrypted wallet that was not unlocked for the request, or no device signature for the exact approved PSBT, `SIGNER_UNAVAILABLE`; the device dialog cancelled (including when no device is connected), a wrong password or a refused replay acknowledgement rejects the prompt (`USER_REJECTED`); not connected, unreachable server or no answer within 30 seconds `NETWORK_UNAVAILABLE`; server refusal `TRANSACTION_REJECTED`; a mainnet or signet broadcast of a transaction not cleared in a PWC signing prompt `POLICY_VIOLATION`; anything else `INTERNAL_ERROR` without sensitive details.
- `local.hello` reports the provider id of the endpoint (`puritywallet`, `puritywallet-signet` or `puritywallet-regtest`). `transaction.broadcast` returns the txid in display order.
- Fee estimates convert the connected node's sat/vB targets to sat/kvB, using the rate of the largest target not above the requested one; no third-party fee source is used.
- PSBTs created for `psbt.create` are version 0, the only version the SDK signs.
- No PWC endpoint is opened on Intel Macs, in terminal mode, or while the switch is off.
