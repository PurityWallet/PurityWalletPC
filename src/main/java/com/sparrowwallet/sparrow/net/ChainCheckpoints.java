package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.*;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Map;

public final class ChainCheckpoints {
    private static final Map<Network, ChainCheckpoints> PROFILES = new EnumMap<>(Network.class);
    private final Network network;
    private final HeaderCheckpoints upstream;

    private ChainCheckpoints(Network network) {
        this.network = network;
        this.upstream = network.getHeaderCheckpoints();
    }

    public static synchronized ChainCheckpoints get(Network network) {
        return PROFILES.computeIfAbsent(network, ChainCheckpoints::new);
    }

    public int getMaxHeight() {
        return network == Network.MAINNET ? PurityHeaderState.COMMON_ANCHOR_HEIGHT : upstream.getMaxHeight();
    }

    public int getMinimumTipHeight() {
        return network == Network.MAINNET ? PurityHeaderState.ACTIVATION_HEIGHT : getMaxHeight();
    }

    public Sha256Hash getHash(int height) {
        if(height > getMaxHeight()) {
            throw new IllegalArgumentException("Height " + height + " is above the chain's last historical checkpoint");
        }
        return upstream.getHash(height);
    }

    public long getBitsAfter(int height) {
        if(height > getMaxHeight()) {
            throw new IllegalArgumentException("Height " + height + " is above the chain's last historical checkpoint");
        }
        return upstream.getBitsAfter(height);
    }

    public int getPinnedHeightAtOrAbove(int height) {
        if(height > getMaxHeight()) {
            throw new IllegalArgumentException("Height " + height + " must be verified by forward synchronization");
        }
        return upstream.getPinnedHeightAtOrAbove(height);
    }

    public String getCacheNamespace() {
        return network == Network.MAINNET ? "purity-mainnet-asert-v1" : null;
    }

    public HeaderState newChainState() {
        return network == Network.MAINNET ? new PurityHeaderState() : new UpstreamState(upstream.newChainState());
    }

    private record UpstreamState(HeaderChainState state) implements HeaderState {
        public void add(BlockHeader header) { state.add(header); }
        public int getHeight() { return state.getHeight(); }
        public Sha256Hash getHash() { return state.getHash(); }
        public BigInteger getChainWork() { return state.getChainWork(); }
    }
}
