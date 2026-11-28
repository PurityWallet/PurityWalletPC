package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.sparrow.io.Storage;
import com.sun.security.auth.module.NTSystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** On-disk files for the LOCAL Provider. */
public class PwcState {
    static final String DIRECTORY = "pwc";
    static final String LEGACY_SNAPSHOT = "provider-state";

    public static File getDirectory() {
        return new File(Storage.getDataDir(), DIRECTORY);
    }

    static void createDirectory(File dir) throws IOException {
        Path path = dir.toPath();
        try {
            Files.createDirectory(path, ownerOnly(path, true));
        } catch(FileAlreadyExistsException e) {
            restrict(path, true, false);
            return;
        }
        restrict(path, true, true);
    }

    static void createFile(Path path) throws IOException {
        Files.createFile(path, ownerOnly(path, false));
        restrict(path, false, true);
    }

    private static FileAttribute<?> ownerOnly(Path path, boolean directory) throws IOException {
        if(!PwcService.platform().startsWith("windows")) {
            return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        }
        List<AclEntry> acl = ownerAcl(path);
        return new FileAttribute<List<AclEntry>>() {
            public String name() {
                return "acl:acl";
            }

            public List<AclEntry> value() {
                return acl;
            }
        };
    }

    private static List<AclEntry> ownerAcl(Path path) throws IOException {
        NTSystem account = new NTSystem();
        if(account.getDomain() == null || account.getName() == null || account.getUserSID() == null) {
            throw new IOException("Could not identify the current Windows account");
        }
        UserPrincipal user = path.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(account.getDomain() + "\\" + account.getName());
        return List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(user)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
    }

    static void restrictFile(Path path) throws IOException {
        restrict(path, false, false);
    }

    private static void restrict(Path path, boolean directory, boolean created) throws IOException {
        boolean expectedType = directory ? Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                : Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
        if(!expectedType) {
            throw new IOException("Invalid PWC state path: " + path);
        }
        if(PwcService.platform().startsWith("windows")) {
            AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if(view == null) {
                throw new IOException("Windows ACLs are unavailable for PWC state: " + path);
            }
            List<AclEntry> expected = ownerAcl(path);
            UserPrincipal user = expected.getFirst().principal();
            if(!created && !view.getOwner().equals(user)) {
                throw new IOException("PWC state must be owned by the current user: " + path);
            }
            if(!view.getAcl().equals(expected)) {
                view.setAcl(expected);
            }
            // Windows can assign a group from the token as the default owner, even with an owner-only ACL.
            if(created && !view.getOwner().equals(user)) {
                view.setOwner(user);
            }
            if(!view.getOwner().equals(user) || !view.getAcl().equals(expected)) {
                throw new IOException("Could not restrict the Windows permissions on " + path);
            }
        } else {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        }
    }

    static void retireLegacySnapshot(File dir) throws IOException {
        Path path = dir.toPath().resolve(LEGACY_SNAPSHOT);
        if(Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        restrictFile(path);
        Files.move(path, path.resolveSibling(LEGACY_SNAPSHOT + ".retired-" + UUID.randomUUID()), StandardCopyOption.ATOMIC_MOVE);
    }
}
