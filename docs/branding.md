# Brand Assets

The artwork comes from `img/puritywallet-logo.png` in the existing local mobile PurityWallet project at `/Users/hsn/code/PurityWallet`. The source project is not modified.

The canonical PNG is copied byte-for-byte to `src/main/resources/image/puritywallet-logo.png`. Its SHA-256 is `4c77b0cf5d54172a5194ba6f97c616f6848e0f52e3ac3e916630c24a906f0111`.
All desktop icon sizes are scaled from this transparent master with macOS `sips`. Resizing preserves the artwork, colors, aspect ratio and alpha channel.
The mobile project's opaque system icons are unsuitable for desktop light themes because they introduce black rectangles.

| Desktop destination | Sizes in pixels | Use |
| --- | --- | --- |
| `src/main/resources/image/puritywallet-logo.png` | 1024 | Canonical UI logo, dialogs and wallet-model views |
| `src/main/resources/image/puritywallet-icon.png` and `@2x` | 128, 256 | Window icons |
| `src/main/resources/image/puritywallet-tray*.png` | 16, 32, 64 | System tray icons |
| `src/main/deploy/package/linux/puritywallet.png` | 512 | Linux application icon |
| `src/main/deploy/package/macos/puritywallet.icns` | 16 through 1024, including Retina representations | macOS application icon |
| `src/main/deploy/package/windows/puritywallet.ico` | 16, 32, 64, 128, 256 | Windows application icon |

ICNS and ICO contain the scaled PNGs without further pixel conversion. The logo keeps its original blue-and-white colors in both light and dark themes.

All old Sparrow artwork is removed from this project's image resources. Internal Java packages and `WalletModel.SPARROW` remain compatible with upstream wallet files. Other wallet vendors retain their own artwork.
