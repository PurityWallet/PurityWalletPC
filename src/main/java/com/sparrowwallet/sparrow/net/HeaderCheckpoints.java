package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.Sha256Hash;

public class HeaderCheckpoints {
    public static final int PURITY_ACTIVATION_HEIGHT = 961637;
    public static final Sha256Hash PURITY_ACTIVATION_HASH = Sha256Hash.wrap("0000000000000000003ea74f4dafdda7ed4e02c4c1ccb9768e0ca4f9e1a35159");
    private static final int LAST_SHARED_CHECKPOINT = 961631;

    private final Network network;
    private final com.sparrowwallet.drongo.protocol.HeaderCheckpoints sharedCheckpoints;

    private HeaderCheckpoints(Network network) {
        this.network = network;
        this.sharedCheckpoints = network.getHeaderCheckpoints();
    }

    public static HeaderCheckpoints get(Network network) {
        return new HeaderCheckpoints(network);
    }

    public int getMaxHeight() {
        return network == Network.MAINNET ? PURITY_ACTIVATION_HEIGHT : sharedCheckpoints.getMaxHeight();
    }

    public Sha256Hash getHash(int height) {
        if(network == Network.MAINNET) {
            if(height == PURITY_ACTIVATION_HEIGHT) {
                return PURITY_ACTIVATION_HASH;
            }
            if(height > LAST_SHARED_CHECKPOINT) {
                throw new IllegalArgumentException("Height " + height + " is not a Bitcoin Purity checkpoint");
            }
        }
        return sharedCheckpoints.getHash(height);
    }

    public int getPinnedHeightAtOrAbove(int height) {
        if(network == Network.MAINNET && height > LAST_SHARED_CHECKPOINT && height <= PURITY_ACTIVATION_HEIGHT) {
            return PURITY_ACTIVATION_HEIGHT;
        }
        if(height > getMaxHeight()) {
            throw new IllegalArgumentException("Height " + height + " has no Bitcoin Purity checkpoint at or above it");
        }
        return sharedCheckpoints.getPinnedHeightAtOrAbove(height);
    }

    public long getBitsAfter(int pinnedHeight) {
        if(network == Network.MAINNET && pinnedHeight == PURITY_ACTIVATION_HEIGHT) {
            return 0x1825e9c9L;
        }
        getHash(pinnedHeight);
        return sharedCheckpoints.getBitsAfter(pinnedHeight);
    }

    public HeaderChainState newChainState() {
        int height = getMaxHeight();
        return new HeaderChainState(height, getHash(height), getBitsAfter(height));
    }
}
