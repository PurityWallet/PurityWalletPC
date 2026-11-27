package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.sparrow.io.Storage;

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
            if(!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw e;
            }
        }
        restrict(path, true);
    }

    static FileAttribute<?> ownerOnly(Path path, boolean directory) throws IOException {
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
        UserPrincipal user = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
        return List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(user)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
    }

    static void restrict(Path path, boolean directory) throws IOException {
        boolean expectedType = directory ? Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                : Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
        if(!expectedType) {
            throw new IOException("Invalid PWC state path: " + path);
        }
        if(PwcService.platform().startsWith("windows")) {
            AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            List<AclEntry> expected = ownerAcl(path);
            if(view == null || !view.getOwner().equals(expected.getFirst().principal())) {
                throw new IOException("PWC state must be owned by the current user: " + path);
            }
            if(!view.getAcl().equals(expected)) {
                view.setAcl(expected);
            }
            if(!view.getAcl().equals(expected)) {
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
        restrict(path, false);
        Files.move(path, path.resolveSibling(LEGACY_SNAPSHOT + ".retired-" + UUID.randomUUID()), StandardCopyOption.ATOMIC_MOVE);
    }
}
