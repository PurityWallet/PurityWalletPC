# TASK-002: PurityWallet Artwork and English Documentation

Source: `input/requirements.md`.

- [x] Add failing regression checks for canonical artwork, native icon containers, old artwork references and documentation language.
- [x] Reuse the local PurityWallet project's transparent logo at the required desktop icon sizes.
- [x] Replace Sparrow UI graphics and remove obsolete assets.
- [x] Translate all project documentation into English and synchronize the specification and migration report.
- [x] Run regression checks, verify the UI artwork and rebuild the macOS application and ZIP.

Do not modify dependency sources. Preserve upstream license attribution and wallet-format identifiers.

Validation completed:

- 352 Java regression tests passed.
- Nine Python checks passed, covering branding, transparency, PNG sizes, native icon containers, old artwork references, launchers, license notices and English documentation.
- JavaFX dialog and wallet-model controls were rendered and visually checked in light and dark themes. Window and tray icons were included in the preview.
- The macOS application and `puritywallet-2.5.6.zip` were rebuilt. The ZIP contains the new transparent native icon and the current runtime image.
- The canonical 1024-pixel logo retains the exact source bytes from the mobile project; no dependency source was modified.

Windows and Linux native builds and execution were not verified. Their icon formats and resource references were checked locally.
