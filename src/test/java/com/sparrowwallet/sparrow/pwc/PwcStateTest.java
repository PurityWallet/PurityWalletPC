package com.sparrowwallet.sparrow.pwc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;

class PwcStateTest {
    @TempDir
    File dir;

    @Test
    void missingSnapshotLoadsAsNull() throws Exception {
        assertNull(new PwcState(new File(dir, "pwc")).load());
    }

    @Test
    void savedSnapshotLoadsBackAndReplacesThePreviousOne() throws Exception {
        PwcState state = new PwcState(new File(dir, "pwc"));
        state.save("first".getBytes(StandardCharsets.UTF_8));
        state.save("second".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals("second".getBytes(StandardCharsets.UTF_8), state.load());
        assertArrayEquals(new String[] {PwcState.STATE_FILE}, new File(dir, "pwc").list());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void snapshotIsReadableByTheOwnerOnly() throws Exception {
        File pwcDir = new File(dir, "pwc");
        pwcDir.mkdirs();
        Files.setPosixFilePermissions(pwcDir.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"));
        new PwcState(pwcDir).save(new byte[] {1, 2, 3});
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(pwcDir.toPath())));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(new File(pwcDir, PwcState.STATE_FILE).toPath())));
    }

    @Test
    void quarantineKeepsTheCorruptSnapshotAside() throws Exception {
        File pwcDir = new File(dir, "pwc");
        PwcState state = new PwcState(pwcDir);
        state.save(new byte[] {9});
        File kept = state.quarantine();
        assertNull(state.load());
        assertTrue(kept.getName().startsWith(PwcState.STATE_FILE + ".corrupt-"), kept.getName());
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(kept.toPath()));
    }
}
