package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.net.LegacyUtxoChecker;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PwcReplayTest {
    @Test
    void onlyVerifiedPreviousTransactionsSupplyReplayMetadata() throws Exception {
        Script script = new Script(new byte[] {0x51});
        Transaction previous = new Transaction();
        previous.addInput(new Transaction().getTxId(), 0, new Script(new byte[0]));
        previous.addOutput(10_000, script);
        var txid = previous.getTxId();
        Transaction spending = new Transaction();
        spending.addInput(txid, 0, new Script(new byte[0]));
        spending.addOutput(8_000, script);
        PSBT psbt = new PSBT(spending);
        psbt.getPsbtInputs().getFirst().setWitnessUtxo(new TransactionOutput(null, 9_000, script));

        var unknown = new LegacyUtxoChecker.Utxo(txid.toString(), 0, -1, null);
        var verified = new LegacyUtxoChecker.Utxo(txid.toString(), 0, 10_000, "51");
        var unverified = PwcPromptHandler.replayInputs(psbt, Map.of());
        assertEquals(unknown, unverified.getFirst());
        var checker = new LegacyUtxoChecker(path -> {
            fail("A prevout with no verified metadata must remain unknown without querying the service");
            return null;
        });
        assertEquals(LegacyUtxoChecker.Status.UNKNOWN, LegacyUtxoChecker.summarize(unverified, checker.check(unverified)));

        Map<Sha256Hash, BlockTransaction> history =
                Map.of(txid, new BlockTransaction(txid, 1, null, null, previous));
        assertEquals(verified, PwcPromptHandler.replayInputs(psbt, history).getFirst());

        psbt.getPsbtInputs().getFirst().setWitnessUtxo(null);
        psbt.getPsbtInputs().getFirst().setNonWitnessUtxo(previous);
        assertEquals(verified, PwcPromptHandler.replayInputs(psbt, Map.of()).getFirst());
    }
}
