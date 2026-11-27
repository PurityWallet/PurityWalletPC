package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.wallet.Wallet;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
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
        if(Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] salt = new byte[32];
            new SecureRandom().nextBytes(salt);
            Files.createFile(file, PwcState.ownerOnly(file, false));
            PwcState.restrict(file, false);
            Files.write(file, salt);
        } else {
            PwcState.restrict(file, false);
        }
        return new PwcIds(Files.readAllBytes(file));
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
