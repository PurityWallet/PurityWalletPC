import pathlib
import hashlib
import struct
import plistlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]

class PackagingTest(unittest.TestCase):
    def test_beta_release_version(self):
        build = (ROOT / "build.gradle").read_text()
        self.assertIn("version = '1.0.0b'", build)
        self.assertIn("appVersion = version.toString().replaceFirst(/b$/, '')", build)
        with (ROOT / "src/main/deploy/package/macos/Info.plist").open("rb") as stream:
            info = plistlib.load(stream)
        self.assertEqual("1.0.0", info["CFBundleShortVersionString"])
        self.assertEqual("1.0.0", info["CFBundleVersion"])

    def test_launchers(self):
        self.assertTrue((ROOT / "puritywallet").is_file())
        self.assertTrue((ROOT / "puritywallet.bat").is_file())
        self.assertFalse((ROOT / "sparrow").exists())
        build = (ROOT / "build.gradle").read_text()
        self.assertIn("name = 'puritywallet'", build)
        self.assertIn('imageName = "puritywallet"', build)
        self.assertNotIn("Craig Raw (UPLVMSK9D7)", build)

    def test_windows_installer_workflow(self):
        workflow = (ROOT / ".github/workflows/windows-package.yaml").read_text()
        self.assertIn("runs-on: windows-2022", workflow)
        self.assertIn("java-version: '25.0.2'", workflow)
        self.assertIn("./gradlew :test :jpackage", workflow)
        self.assertIn("puritywallet-${env:RELEASE_VERSION}-windows-x64.msi", workflow)
        self.assertIn("msiexec.exe", workflow)
        self.assertIn("--version", workflow)
        self.assertIn("actions/upload-artifact@", workflow)
        self.assertNotIn("matrix:", workflow)

    def test_macos_identity(self):
        with (ROOT / "src/main/deploy/package/macos/Info.plist").open("rb") as f:
            info = plistlib.load(f)
        self.assertEqual("puritywallet", info["CFBundleExecutable"])
        self.assertEqual("PurityWallet", info["CFBundleName"])
        self.assertEqual("org.bitcoinpurity.puritywallet", info["CFBundleIdentifier"])

    def test_license_and_release_notices(self):
        self.assertIn("Apache License", (ROOT / "LICENSE").read_text())
        self.assertIn("Sparrow", (ROOT / "NOTICE").read_text())
        self.assertIn("copyDistributionNotices", (ROOT / "build.gradle").read_text())

class BrandingTest(unittest.TestCase):
    def test_canonical_mobile_logo_is_preserved(self):
        logo = ROOT / "src/main/resources/image/puritywallet-logo.png"
        self.assertEqual("4c77b0cf5d54172a5194ba6f97c616f6848e0f52e3ac3e916630c24a906f0111", hashlib.sha256(logo.read_bytes()).hexdigest())

    def test_no_sparrow_artwork_or_ui_references_remain(self):
        image_root = ROOT / "src/main/resources/image"
        self.assertEqual([], list(image_root.rglob("sparrow*")))
        for base in ["src/main/java", "src/main/resources"]:
            for path in (ROOT / base).rglob("*"):
                if path.suffix not in {".java", ".fxml", ".css"}:
                    continue
                content = path.read_text()
                self.assertNotIn("DialogImage.Type.SPARROW", content, str(path))
                self.assertNotIn('type="SPARROW"', content, str(path))
                self.assertNotRegex(content, r'/image/[^"\s]*sparrow', str(path))

    def test_desktop_icons_preserve_the_transparent_background(self):
        paths = list((ROOT / "src/main/resources/image").glob("puritywallet*.png"))
        paths.append(ROOT / "src/main/deploy/package/linux/puritywallet.png")
        for path in paths:
            self.assertEqual(6, path.read_bytes()[25], str(path))

    def test_windows_icon_contains_multiple_png_sizes(self):
        icon = (ROOT / "src/main/deploy/package/windows/puritywallet.ico").read_bytes()
        reserved, kind, count = struct.unpack_from("<HHH", icon)
        self.assertEqual((0, 1, 5), (reserved, kind, count))
        sizes = []
        for i in range(count):
            width, height, colors, reserved, planes, depth, length, offset = struct.unpack_from("<BBBBHHII", icon, 6 + i * 16)
            data = icon[offset:offset + length]
            self.assertTrue(data.startswith(b"\x89PNG\r\n\x1a\n"))
            png_size = struct.unpack_from(">II", data, 16)
            size = width or 256
            self.assertEqual((size, size), png_size)
            sizes.append(size)
        self.assertEqual([16, 32, 64, 128, 256], sizes)

    def test_macos_icon_contains_full_resolution_artwork(self):
        icon = (ROOT / "src/main/deploy/package/macos/puritywallet.icns").read_bytes()
        self.assertEqual(b"icns", icon[:4])
        self.assertEqual(len(icon), struct.unpack_from(">I", icon, 4)[0])
        offset = 8
        sizes = set()
        while offset < len(icon):
            length = struct.unpack_from(">I", icon, offset + 4)[0]
            data = icon[offset + 8:offset + length]
            if data.startswith(b"\x89PNG\r\n\x1a\n"):
                sizes.add(struct.unpack_from(">II", data, 16))
            offset += length
        self.assertTrue({(16, 16), (32, 32), (128, 128), (256, 256), (512, 512), (1024, 1024)} <= sizes)

class DocumentationTest(unittest.TestCase):
    def test_project_documentation_is_english(self):
        paths = list((ROOT / "docs").rglob("*.md")) + list((ROOT / "input").rglob("*.md"))
        paths += list(ROOT.glob("*.md"))
        for path in paths:
            self.assertNotRegex(path.read_text(), r"[\u3400-\u9fff]", str(path))

if __name__ == "__main__":
    unittest.main()
