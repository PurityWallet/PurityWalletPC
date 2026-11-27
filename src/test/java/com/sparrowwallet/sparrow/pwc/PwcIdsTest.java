package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PwcIdsTest {
    @TempDir
    File dir;

    @BeforeEach
    void regtest() {
        Network.set(Network.REGTEST);
    }

    @Test
    void idsAreStableAcrossLoadsWithTheSameSalt() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        PwcIds first = PwcIds.load(dir);
        PwcIds second = PwcIds.load(dir);
        assertEquals(first.walletId(wallet), second.walletId(wallet));
        assertEquals(first.accountId(wallet), second.accountId(wallet));
    }

    @Test
    void idsDoNotDependOnTheWalletName() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        Wallet renamed = wallet.copy();
        renamed.setName("Renamed");
        PwcIds ids = PwcIds.load(dir);
        assertEquals(ids.accountId(wallet), ids.accountId(renamed));
    }

    @Test
    void walletAndAccountIdsAreDistinctOpaqueHex() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        PwcIds ids = PwcIds.load(dir);
        String walletId = ids.walletId(wallet);
        String accountId = ids.accountId(wallet);
        assertNotEquals(walletId, accountId);
        assertTrue(walletId.matches("[0-9a-f]{32}"), walletId);
        assertTrue(accountId.matches("[0-9a-f]{32}"), accountId);
        String descriptor = OutputDescriptor.getOutputDescriptor(wallet).toString(true);
        String fingerprint = wallet.getKeystores().getFirst().getKeyDerivation().getMasterFingerprint();
        assertFalse(descriptor.contains(walletId) || walletId.contains(fingerprint) || accountId.contains(fingerprint));
    }

    @Test
    void differentAccountsGetDifferentIds() throws Exception {
        PwcIds ids = PwcIds.load(dir);
        assertNotEquals(ids.accountId(PwcTestWallets.software("A", ScriptType.P2WPKH)), ids.accountId(PwcTestWallets.software("B", ScriptType.P2PKH)));
        assertNotEquals(ids.accountId(PwcTestWallets.software("A", ScriptType.P2WPKH)),
                ids.accountId(PwcTestWallets.software("A", ScriptType.P2WPKH, PwcTestWallets.OTHER_SEED)));
    }

    @Test
    void anotherInstallationCannotLinkTheIds(@TempDir File otherDir) throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        assertNotEquals(PwcIds.load(dir).accountId(wallet), PwcIds.load(otherDir).accountId(wallet));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void saltIsReadableByTheOwnerOnly() throws Exception {
        File pwcDir = new File(dir, "pwc");
        PwcIds.load(pwcDir);
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(pwcDir.toPath())));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(new File(pwcDir, PwcIds.SALT_FILE).toPath())));
        assertEquals(32, Files.size(new File(pwcDir, PwcIds.SALT_FILE).toPath()));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void directoryAndSaltAclAllowOnlyTheOwner() throws Exception {
        File pwcDir = new File(dir, "pwc");
        PwcIds.load(pwcDir);
        for(var path : List.of(pwcDir.toPath(), pwcDir.toPath().resolve(PwcIds.SALT_FILE))) {
            var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            var owner = acl.getOwner();
            assertFalse(acl.getAcl().isEmpty());
            assertTrue(acl.getAcl().stream().allMatch(entry -> entry.principal().equals(owner)));
        }
    }
}
