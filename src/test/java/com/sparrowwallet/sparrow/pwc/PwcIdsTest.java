package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sun.security.auth.module.NTSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PwcIdsTest {
    @TempDir
    File dir;

    private File pwcDir;

    @BeforeEach
    void regtest() {
        Network.set(Network.REGTEST);
        pwcDir = new File(dir, "pwc");
    }

    @Test
    void idsAreStableAcrossLoadsWithTheSameSalt() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        PwcIds first = PwcIds.load(pwcDir);
        PwcIds second = PwcIds.load(pwcDir);
        assertEquals(first.walletId(wallet), second.walletId(wallet));
        assertEquals(first.accountId(wallet), second.accountId(wallet));
    }

    @Test
    void idsDoNotDependOnTheWalletName() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        Wallet renamed = wallet.copy();
        renamed.setName("Renamed");
        PwcIds ids = PwcIds.load(pwcDir);
        assertEquals(ids.accountId(wallet), ids.accountId(renamed));
    }

    @Test
    void walletAndAccountIdsAreDistinctOpaqueHex() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        PwcIds ids = PwcIds.load(pwcDir);
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
        PwcIds ids = PwcIds.load(pwcDir);
        assertNotEquals(ids.accountId(PwcTestWallets.software("A", ScriptType.P2WPKH)), ids.accountId(PwcTestWallets.software("B", ScriptType.P2PKH)));
        assertNotEquals(ids.accountId(PwcTestWallets.software("A", ScriptType.P2WPKH)),
                ids.accountId(PwcTestWallets.software("A", ScriptType.P2WPKH, PwcTestWallets.OTHER_SEED)));
    }

    @Test
    void anotherInstallationCannotLinkTheIds(@TempDir File otherDir) throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        assertNotEquals(PwcIds.load(pwcDir).accountId(wallet), PwcIds.load(new File(otherDir, "pwc")).accountId(wallet));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void saltIsReadableByTheOwnerOnly() throws Exception {
        PwcIds.load(pwcDir);
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(pwcDir.toPath())));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(new File(pwcDir, PwcIds.SALT_FILE).toPath())));
        assertEquals(32, Files.size(new File(pwcDir, PwcIds.SALT_FILE).toPath()));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void directoryAndSaltAclAllowOnlyTheOwner() throws Exception {
        NTSystem windowsUser = new NTSystem();
        var currentUser = pwcDir.toPath().getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(windowsUser.getDomain() + "\\" + windowsUser.getName());
        var onlyCurrentUser = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(currentUser)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();

        String oldName = System.setProperty("user.name", "pwc-test-invalid-account");
        try {
            PwcIds.load(pwcDir);
            PwcIds.load(pwcDir);
        } finally {
            System.setProperty("user.name", oldName);
        }

        for(var path : List.of(pwcDir.toPath(), pwcDir.toPath().resolve(PwcIds.SALT_FILE))) {
            var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            assertEquals(currentUser, acl.getOwner());
            assertEquals(List.of(onlyCurrentUser), acl.getAcl());
        }
    }
}
