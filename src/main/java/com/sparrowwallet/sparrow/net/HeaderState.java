package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import java.math.BigInteger;

public interface HeaderState {
    void add(BlockHeader header);
    int getHeight();
    Sha256Hash getHash();
    BigInteger getChainWork();
}
