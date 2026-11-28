package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.wallet.Wallet;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/** Opaque PWC wallet and account ids: an HMAC of the output descriptor under a random per-installation salt. */
public class PwcIds {
    static final String SALT_FILE = "id-salt";

    private final byte[] salt;

    private PwcIds(byte[] salt) {
        this.salt = salt;
    }

    public static PwcIds load(File dir) throws IOException {
        PwcState.createDirectory(dir);
        Path file = dir.toPath().resolve(SALT_FILE);
        try {
            PwcState.createFile(file);
        } catch(FileAlreadyExistsException e) {
            PwcState.restrictFile(file);
        }
        byte[] salt = new byte[32];
        try(var channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            try(var lock = channel.lock()) {
                ByteBuffer bytes = ByteBuffer.wrap(salt);
                if(channel.size() == 0) {
                    new SecureRandom().nextBytes(salt);
                    while(bytes.hasRemaining()) {
                        channel.write(bytes);
                    }
                    channel.force(true);
                } else {
                    if(channel.size() != salt.length) {
                        throw new IOException("Invalid PWC account ID salt length");
                    }
                    while(bytes.hasRemaining()) {
                        if(channel.read(bytes) < 0) {
                            throw new IOException("Incomplete PWC account ID salt");
                        }
                    }
                }
            }
        }
        return new PwcIds(salt);
    }

    public String walletId(Wallet masterWallet) {
        return id("wallet", masterWallet);
    }

    public String accountId(Wallet account) {
        return id("account", account);
    }

    private String id(String domain, Wallet wallet) {
        String descriptor = OutputDescriptor.getOutputDescriptor(wallet).toString(true);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] digest = mac.doFinal((domain + "\u0000" + descriptor).getBytes(StandardCharsets.UTF_8));
            return Utils.bytesToHex(Arrays.copyOf(digest, 16));
        } catch(GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
