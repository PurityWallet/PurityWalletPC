package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.sparrow.io.Storage;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;

/** The Provider snapshot: pairings and grants, readable by the owner only. */
public class PwcState {
    static final String DIRECTORY = "pwc";
    static final String STATE_FILE = "provider-state";
    private static final String SESSION_FILE = "provider-session";

    private final Path dir;

    public PwcState(File dir) {
        this.dir = dir.toPath();
    }

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

    public byte[] load() throws IOException {
        Path file = dir.resolve(STATE_FILE);
        if(Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        restrict(file, false);
        return Files.readAllBytes(file);
    }

    public synchronized void save(byte[] snapshot) throws IOException {
        createDirectory(dir.toFile());
        Path temp = Files.createTempFile(dir, STATE_FILE, ".tmp", ownerOnly(dir, false));
        try {
            restrict(temp, false);
            try(FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer data = ByteBuffer.wrap(snapshot);
                while(data.hasRemaining()) {
                    channel.write(data);
                }
                channel.force(true);
            }
            Files.move(temp, dir.resolve(STATE_FILE), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    Session openSession() throws IOException {
        createDirectory(dir.toFile());
        Path path = dir.resolve(SESSION_FILE);
        try {
            Files.createFile(path, ownerOnly(path, false));
        } catch(FileAlreadyExistsException e) {
            if(!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw e;
            }
        }
        restrict(path, false);
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        boolean locked = false;
        try {
            locked = channel.tryLock() != null;
            if(locked) {
                return new Session(channel);
            }
        } catch(OverlappingFileLockException e) {
            // Another PWC service in the same process already owns this state.
        } finally {
            if(!locked) {
                channel.close();
            }
        }
        throw new IOException("Another PurityWallet instance already serves PWC using " + dir);
    }

    /** SDK 0.2.1 does not notify the host of protocol revocations. A stale snapshot cannot be restored after a crash. */
    record Session(FileChannel channel) implements AutoCloseable {
        boolean interrupted() throws IOException {
            ByteBuffer marker = ByteBuffer.allocate(1);
            return channel.read(marker, 0) != -1 && marker.get(0) != 0;
        }

        void markRunning() throws IOException {
            writeMarker((byte)1);
        }

        void markStopped() throws IOException {
            writeMarker((byte)0);
        }

        private void writeMarker(byte value) throws IOException {
            ByteBuffer marker = ByteBuffer.wrap(new byte[] {value});
            while(marker.hasRemaining()) {
                channel.write(marker, 0);
            }
            channel.force(true);
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    public File quarantine() throws IOException {
        Path kept = dir.resolve(STATE_FILE + ".corrupt-" + System.currentTimeMillis());
        Files.move(dir.resolve(STATE_FILE), kept);
        return kept.toFile();
    }
}
