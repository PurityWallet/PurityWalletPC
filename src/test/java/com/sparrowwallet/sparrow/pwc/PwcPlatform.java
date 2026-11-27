package com.sparrowwallet.sparrow.pwc;

import java.util.Set;

/** The platforms whose native library the PWC SDK 0.2.1 jar carries. */
final class PwcPlatform {
    private PwcPlatform() {}

    static boolean hasNativeLibrary() {
        return Set.of("linux-x86_64", "linux-aarch64", "macos-aarch64", "windows-x86_64").contains(PwcService.platform());
    }

    static boolean lacksNativeLibrary() {
        return !hasNativeLibrary();
    }

    static boolean isWindows() {
        return PwcService.platform().startsWith("windows");
    }
}
