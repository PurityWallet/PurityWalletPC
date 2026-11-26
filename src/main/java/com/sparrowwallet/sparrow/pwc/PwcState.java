package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.sparrow.io.Storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** The Provider snapshot: pairings and grants, readable by the owner only. */
public class PwcState {
    static final String DIRECTORY = "pwc";
    static final String STATE_FILE = "provider-state";

    private final File dir;

    public PwcState(File dir) {
        this.dir = dir;
    }

    public static File getDirectory() {
        return new File(Storage.getDataDir(), DIRECTORY);
    }

    static void createDirectory(File dir) throws IOException {
        if(!dir.exists() && !Storage.createOwnerOnlyDirectory(dir)) {
            throw new IOException("Could not create " + dir);
        }
        Storage.setOwnerOnlyDirectory(dir);
    }

    public byte[] load() throws IOException {
        File file = new File(dir, STATE_FILE);
        return file.exists() ? Files.readAllBytes(file.toPath()) : null;
    }

    public synchronized void save(byte[] snapshot) throws IOException {
        createDirectory(dir);
        File temp = new File(dir, STATE_FILE + ".tmp");
        Files.deleteIfExists(temp.toPath());
        if(!Storage.createOwnerOnlyFile(temp)) {
            throw new IOException("Could not create " + temp);
        }
        Files.write(temp.toPath(), snapshot);
        Files.move(temp.toPath(), new File(dir, STATE_FILE).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public File quarantine() throws IOException {
        File kept = new File(dir, STATE_FILE + ".corrupt-" + System.currentTimeMillis());
        Files.move(new File(dir, STATE_FILE).toPath(), kept.toPath());
        return kept;
    }
}
