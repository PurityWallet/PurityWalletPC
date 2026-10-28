# PurityWallet

PurityWallet is a desktop wallet for Bitcoin Purity, adapted from [Sparrow Wallet](https://github.com/sparrowwallet/sparrow). It retains desktop software/hardware wallets, multisig, watch-only wallets and PSBT workflows.

**Purity mainnet verification is adapted in the application:** shared historical checkpoint 961631, fixed activation block 961637 and integer ASERT validation replace the Bitcoin-only forward rules without changing dependency sources. Read-only acceptance verified every header from 961632 through 971156, cache replay and a post-fork transaction proof. See [migration results](docs/migration-report.md) for commands and platform/release limits.

## Purity defaults

- Public Electrum: `ssl://electrum.bitcoinpurity.org:50002` (TLS, mainnet).
- Explorer: [Purity Mempool](https://mempool.bitcoinpurity.org).
- Custom Electrum/Core-compatible connections and custom explorers remain supported.
- Automatic fees come from the connected node; manual fees remain available.
- Fiat valuations, external Bitcoin fee sources and Sparrow update checks are disabled, including with old settings.
- Donate in About uses the normal Send workflow. The shipped recipient is an invalid placeholder, so donations are unavailable until configured.

[Documentation](docs/README.md) · [Issues](https://github.com/PurityWallet/PurityWalletPC/issues) · [Releases](https://github.com/PurityWallet/PurityWalletPC/releases)

## Building

Clone this repository and initialize its pinned submodules:

```sh
git clone --recursive git@github.com:PurityWallet/PurityWalletPC.git
```

In order to build, PurityWallet requires Java 25 or higher to be installed.
The release binaries are built with [Eclipse Temurin 25.0.2+10](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.2%2B10).
If you are using [SDKMAN](https://sdkman.io/), you can use `sdk env install` to ensure you have the correct version.

Other packages may also be necessary to build depending on the platform. On Debian/Ubuntu systems:

`sudo apt install -y rpm fakeroot binutils`

The PurityWallet binaries can be built from source using

`./gradlew jpackage`

On Linux distributions without `deb` or `rpm` packaging tools installed (such as Arch), building the installers can be skipped with

`./gradlew jpackage -PskipInstallers=true`

Note that to build the Windows installer, you will need to install [WiX](https://github.com/wixtoolset/wix3/releases).

When updating to the latest HEAD

`git pull --recurse-submodules`

The release binaries are reproducible from v1.5.0 onwards (pre codesigning and installer packaging). More detailed [instructions on reproducing the binaries](docs/reproducible.md) are provided.

> Video documentation of your build process uploaded to [bitcoinbinary.org](https://bitcoinbinary.org/) is appreciated. Alternatively check the site if you wish to see if someone else already verified the provided binaries.

## Running

If you prefer to run PurityWallet directly from source, it can be launched from within the project directory with

`./sparrow`

Java 25 or higher must be installed.

## Configuration

PurityWallet has a number of command line options, for example to change its home folder or use testnet:

```
./sparrow -h

Usage: puritywallet [options]
  Options:
    --dir, -d
      Path to PurityWallet home folder
    --help, -h
      Show usage
    --level, -l
      Set log level
      Possible Values: [ERROR, WARN, INFO, DEBUG, TRACE]
    --network, -n
      Network to use
      Possible Values: [mainnet, testnet, regtest, signet, testnet4]
```

Note that testnet currently refers to testnet3.

As a fallback, the network (mainnet, testnet, testnet4, regtest or signet) can also be set using an environment variable `SPARROW_NETWORK`. For example:

`export SPARROW_NETWORK=testnet`

A final fallback which can be useful when running the PurityWallet binary is to create a file called ``network-testnet`` in the Sparrow home folder (see below) to configure the testnet network.

Note that if you are connecting to an Electrum server when using testnet, that server will need to be running on testnet configuration as well.

For storage compatibility with upstream, PurityWallet stores its mainnet config file, log file and wallets in a home folder location appropriate to the operating system:

| Platform | Location |
|----------| -------- |
| macOS    | ~/.sparrow |
| Linux    | ~/.sparrow |
| Windows  | %APPDATA%/Sparrow |

Testnet3, testnet4, regtest and signet configurations (along with their wallets) are stored in subfolders to allow easy switching between networks.

On macOS and Linux, PurityWallet also supports the [XDG Base Directory Specification](https://specifications.freedesktop.org/basedir-spec/latest/).
This is opt in: for each category below, if the corresponding directory already exists, Sparrow uses it, otherwise it continues to use the home folder above. Categories are resolved independently, so files can be moved across one at a time.

| Category | Location | Contents                              |
|----------| -------- |---------------------------------------|
| Config   | `$XDG_CONFIG_HOME/sparrow` (default `~/.config/sparrow`) | `config`, `network-*` markers         |
| Data     | `$XDG_DATA_HOME/sparrow` (default `~/.local/share/sparrow`) | `wallets`, `certs`, `lark`            |
| State    | `$XDG_STATE_HOME/sparrow` (default `~/.local/state/sparrow`) | `sparrow.log`, `tor/work`, lock files |
| Cache    | `$XDG_CACHE_HOME/sparrow` (default `~/.cache/sparrow`) | `tor/cache`                           |

Specifying a home folder with the `-d` argument disables XDG resolution entirely, and stores all files in the given folder.

## Compatibility

The existing Sparrow application identities and data directories are intentionally retained. Independent coexistence with Sparrow is not guaranteed. No automatic wallet migration is performed. Wallet files, PSBT, URI schemes and import formats remain compatible. `SPARROW_NETWORK`, `sparrow.home` and the `sparrow` development script retain their existing names. Only Purity mainnet public defaults are bundled; other modes require manual configuration and no Purity Signet support is added.

## Verification

```sh
./gradlew :test
./gradlew :desktopTest
./gradlew :verifyCheckpoint
./gradlew :jpackageImage
```

The UI tests require a graphical desktop and save previews under `build/qa/`. The checkpoint command is an explicit read-only live gate, covering shared historical checkpoints and full Purity forward synchronization, replay and inclusion-proof checks. It requires access to the configured public Electrum service. No validation switch is disabled.

Desktop icon containers are generated by `python3 scripts/generate-icons.py` on macOS using `sips`. The checked-in 1024 px master is copied unchanged from the mobile blue PurityWallet icon (SHA-256 `4c77b0cf5d54172a5194ba6f97c616f6848e0f52e3ac3e916630c24a906f0111`). Existing asset filenames are retained for compatibility.

## License and upstream attribution

PurityWallet retains Sparrow Wallet's Apache 2.0 license and upstream copyright notices; see [LICENSE](LICENSE). Upstream signing keys and release signatures do not authenticate PurityWallet releases. Signing, notarization and distribution are outside this change.
