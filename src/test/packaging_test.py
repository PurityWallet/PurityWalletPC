import pathlib
import hashlib
import struct
import plistlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]

PWC_TOKEN = "PWC_PACKAGES_TOKEN: ${{ secrets.PWC_PACKAGES_TOKEN }}"
PWC_USER = "PWC_PACKAGES_USER: ${{ github.actor }}"

class PackagingTest(unittest.TestCase):
    def assert_only_pwc_packages_secret(self, workflow):
        """The read-only GitHub Packages token for the PWC SDK is the only secret a workflow may use (REQ-014)."""
        self.assertIn(PWC_TOKEN, workflow)
        self.assertIn(PWC_USER, workflow)
        self.assertNotIn("secrets.", workflow.replace("secrets.PWC_PACKAGES_TOKEN", ""))
        for line in workflow.splitlines():
            if "PWC_PACKAGES_TOKEN:" in line:
                self.assertEqual(10, len(line) - len(line.lstrip(" ")), "the token is passed to individual steps only: " + line)

    def test_release_version(self):
        build = (ROOT / "build.gradle").read_text()
        self.assertIn("version = '1.1.1'", build)
        self.assertIn('appVersion = "${version}"', build)
        with (ROOT / "src/main/deploy/package/macos/Info.plist").open("rb") as stream:
            info = plistlib.load(stream)
        self.assertEqual("1.1.1", info["CFBundleShortVersionString"])
        self.assertEqual("1.1.1", info["CFBundleVersion"])

    def test_launchers(self):
        self.assertTrue((ROOT / "puritywallet").is_file())
        self.assertTrue((ROOT / "puritywallet.bat").is_file())
        self.assertFalse((ROOT / "sparrow").exists())
        build = (ROOT / "build.gradle").read_text()
        self.assertIn("name = 'puritywallet'", build)
        self.assertIn('imageName = "puritywallet"', build)
        self.assertNotIn("Craig Raw (UPLVMSK9D7)", build)

    def test_ci_is_one_workflow_with_three_jobs(self):
        """REQ-015: one workflow, so a CI service that runs one workflow per build covers every check."""
        for name in ["test.yaml", "windows-package.yaml", "macos-package.yaml"]:
            self.assertFalse((ROOT / ".github/workflows" / name).exists(), name)
        workflow = (ROOT / ".github/workflows/ci.yaml").read_text()
        self.assertIn("name: CI", workflow)
        self.assertRegex(workflow, r"(?m)^  tests:\n    runs-on: ubuntu-22.04$")
        self.assertRegex(workflow, r"(?m)^  windows-installer:\n    runs-on: windows-2022$")
        self.assertRegex(workflow, r"(?m)^  macos-dmg:\n    runs-on: macos-15$")
        self.assertNotIn("macos-15-intel", workflow)
        self.assertNotIn("matrix:", workflow)

    def test_ci_runs_for_pr_updates_master_pushes_and_dispatch(self):
        workflow = (ROOT / ".github/workflows/ci.yaml").read_text()
        self.assertRegex(workflow, r"(?m)^  pull_request:\n    branches: \[master\]$")
        self.assertRegex(workflow, r"(?m)^  push:\n    branches:\n      - master$")
        self.assertIn("  workflow_dispatch:", workflow)
        self.assertIn("  contents: read", workflow)
        self.assertNotIn("pull_request_target", workflow)
        self.assert_only_pwc_packages_secret(workflow)

    def test_ci_tests_include_desktop_and_isolated_data(self):
        workflow = (ROOT / ".github/workflows/ci.yaml").read_text()
        self.assertIn("submodules: recursive", workflow)
        self.assertIn("java-version: '25.0.2'", workflow)
        self.assertIn("python3 src/test/packaging_test.py", workflow)
        self.assertIn("xvfb-run --auto-servernum ./gradlew :test :desktopTest", workflow)
        self.assertIn("build/test-results/**", workflow)
        build = (ROOT / "build.gradle").read_text()
        self.assertIn("systemProperty 'puritywallet.home', layout.buildDirectory.dir('test-home')", build)
        self.assertIn("systemProperty 'puritywallet.home', layout.buildDirectory.dir('desktop-test-home')", build)

    def test_ci_builds_and_installs_the_windows_msi(self):
        workflow = (ROOT / ".github/workflows/ci.yaml").read_text()
        self.assertIn("./gradlew :test :jpackage", workflow)
        self.assertIn("puritywallet-${env:RELEASE_VERSION}-windows-x64.msi", workflow)
        self.assertIn("msiexec.exe", workflow)
        self.assertIn("--version", workflow)
        self.assertIn("actions/upload-artifact@", workflow)

    def test_ci_builds_the_apple_silicon_dmg_only(self):
        workflow = (ROOT / ".github/workflows/ci.yaml").read_text()
        self.assertIn("puritywallet-${RELEASE_VERSION}-macos-arm64.dmg", workflow)
        self.assertIn('test "$(lipo -archs "$app/Contents/MacOS/puritywallet")" = arm64', workflow)
        self.assertIn("codesign --verify --deep --strict", workflow)

    def test_every_dependency_workflow_can_download_the_pwc_sdk(self):
        for name in ["package.yaml", "dependency-verification.yaml"]:
            self.assert_only_pwc_packages_secret((ROOT / ".github/workflows" / name).read_text())

    def test_pwc_sdk_dependency_and_native_library_packaging(self):
        build = (ROOT / "build.gradle").read_text()
        self.assertIn("url = uri('https://maven.pkg.github.com/BitcoinPurity/pwc-spec')", build)
        self.assertIn("includeGroup 'org.bitcoinpurity.pwc'", build)
        self.assertIn("System.getenv('PWC_PACKAGES_TOKEN')", build)
        self.assertIn("implementation('org.bitcoinpurity.pwc:pwc-provider-java:0.2.0')", build)
        self.assertEqual(2, build.count('"--enable-native-access=org.bitcoinpurity.pwc.provider"'))
        self.assertIn("glob:/org.bitcoinpurity.pwc.provider/org/bitcoinpurity/pwc/provider/native/**", build)
        self.assertIn("'pwc-provider-java-'   : \"org/bitcoinpurity/pwc/provider/native/${pwcPlatform}/*\"", build)
        self.assertIn("-Djava.library.path=$ROOTDIR\\\\runtime\\\\lib", build)
        self.assertIn("requires org.bitcoinpurity.pwc.provider;", (ROOT / "src/main/java/module-info.java").read_text())
        verification = (ROOT / "gradle/verification-metadata.xml").read_text()
        self.assertIn('<component group="org.bitcoinpurity.pwc" name="pwc-provider-java" version="0.2.0">', verification)
        self.assertIn("PWC-Provider-SDK-MIT.txt", (ROOT / "NOTICE").read_text())
        self.assertIn("MIT License", (ROOT / "THIRD-PARTY-LICENSES/PWC-Provider-SDK-MIT.txt").read_text())

    def test_packaged_apps_load_the_pwc_library_from_the_runtime(self):
        workflow = (ROOT / ".github/workflows/ci.yaml").read_text()
        self.assertIn("runtime\\lib\\pwc_provider_jni.dll", workflow)
        self.assertIn("cannot determine the endpoint directory", workflow)
        self.assertIn("libpwc_provider_jni.dylib", workflow)
        self.assertIn("pwc-provider listening on", workflow)

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
