package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.util.Date;
import java.util.Map;

final class PwcTestWallets {
    static final String SEED = "response seminar brave tip suit recall often sound stick owner lottery motion";
    static final String OTHER_SEED = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
    static final String THIRD_SEED = "legal winner thank year wave sausage worth useful legal winner thank yellow";

    private PwcTestWallets() {}

    static Wallet software(String name, ScriptType scriptType) {
        return software(name, scriptType, SEED);
    }

    static Wallet software(String name, ScriptType scriptType, String seed) {
        try {
            Wallet wallet = new Wallet(name);
            wallet.setPolicyType(PolicyType.SINGLE_HD);
            wallet.setScriptType(scriptType);
            Keystore keystore = Keystore.fromSeed(new DeterministicSeed(seed, "", 0, DeterministicSeed.Type.BIP39), PolicyType.SINGLE_HD,
                    KeyDerivation.parsePath(scriptType.getDefaultDerivationPath()));
            wallet.getKeystores().add(keystore);
            wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_HD, scriptType, wallet.getKeystores(), 1));
            return wallet;
        } catch(Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Wallet watchOnly(Wallet software) {
        Wallet wallet = software.copy();
        wallet.setName(software.getName() + " watch");
        Keystore keystore = wallet.getKeystores().getFirst();
        keystore.setSeed(null);
        keystore.setMasterPrivateExtendedKey(null);
        keystore.setSource(KeystoreSource.SW_WATCH);
        return wallet;
    }

    static Wallet hardware(Wallet software) {
        Wallet wallet = watchOnly(software);
        wallet.setName(software.getName() + " device");
        wallet.getKeystores().getFirst().setSource(KeystoreSource.HW_USB);
        wallet.getKeystores().getFirst().setWalletModel(WalletModel.TREZOR_1);
        return wallet;
    }

    /** A confirmed payment from outside the wallet to its receive address at {@code index}. */
    static BlockTransactionHashIndex fund(Wallet wallet, int index, long value, int height) {
        wallet.getNode(KeyPurpose.RECEIVE).fillToIndex(wallet, index);
        WalletNode node = wallet.getNode(KeyPurpose.RECEIVE).getChildren().stream().filter(n -> n.getIndex() == index).findFirst().orElseThrow();
        Transaction tx = new Transaction();
        tx.addInput(Sha256Hash.twiceOf((wallet.getName() + index + value).getBytes()), 0, new Script(new byte[0]));
        tx.addOutput(value, node.getOutputScript());
        Date date = new Date(1_700_000_000_000L + height);
        BlockTransactionHashIndex reference = new BlockTransactionHashIndex(tx.getTxId(), height, date, null, 0, value);
        node.getTransactionOutputs().add(reference);
        wallet.updateTransactions(Map.of(tx.getTxId(), new BlockTransaction(tx.getTxId(), height, date, null, tx)));
        wallet.setStoredBlockHeight(1000);
        return reference;
    }
}
