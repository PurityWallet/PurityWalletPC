package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.protocol.HeaderChainState;
import com.sparrowwallet.drongo.protocol.HeaderCheckpoints;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class PurityHeaderState implements HeaderState {
    public static final int COMMON_ANCHOR_HEIGHT = 961631;
    public static final int ASERT_ANCHOR_HEIGHT = 961632;
    public static final int ACTIVATION_HEIGHT = 961637;
    public static final Sha256Hash ACTIVATION_HASH = Sha256Hash.wrap("0000000000000000003ea74f4dafdda7ed4e02c4c1ccb9768e0ca4f9e1a35159");
    public static final int SIGNET_ACTIVATION_HEIGHT = 327022;
    public static final Sha256Hash SIGNET_ACTIVATION_HASH = Sha256Hash.wrap("00000004b42cf68f94be9dd89b65e44cae31eee382137221b83b5b261d3e3c20");
    public static final Sha256Hash SIGNET_PARENT_HASH = Sha256Hash.wrap("00000013c351c0d13fc2c8b3547413b3ed30854a2434869c064a5076bab6aad6");
    private static final Sha256Hash COMMON_ANCHOR_HASH = Sha256Hash.wrap("00000000000000000000807f9dc917442a67910426d79ebb2f8aa2149327ce8a");
    private static final BigInteger POW_LIMIT = Network.MAINNET.getProofOfWorkLimit();
    private static final int MEDIAN_TIME_SPAN = 11;
    private final Deque<Long> recentTimes = new ArrayDeque<>(MEDIAN_TIME_SPAN);
    private final long anchorParentTime;
    private final long preActivationBits;
    private final int activationHeight;
    private final Sha256Hash activationHash;
    private final int asertAnchorHeight;
    private final BigInteger powLimit;
    private BigInteger referenceTarget;
    private int height;
    private Sha256Hash hash;
    private BigInteger chainWork = BigInteger.ZERO;

    PurityHeaderState() {
        this(Network.MAINNET);
    }

    PurityHeaderState(Network network) {
        this(network, loadAnchor(network));
    }

    PurityHeaderState(List<BlockHeader> anchorHeaders) {
        this(Network.MAINNET, anchorHeaders);
    }

    private PurityHeaderState(Network network, List<BlockHeader> anchorHeaders) {
        boolean signet = network == Network.SIGNET;
        activationHeight = signet ? SIGNET_ACTIVATION_HEIGHT : ACTIVATION_HEIGHT;
        activationHash = signet ? SIGNET_ACTIVATION_HASH : ACTIVATION_HASH;
        asertAnchorHeight = signet ? SIGNET_ACTIVATION_HEIGHT - 5 : ASERT_ANCHOR_HEIGHT;
        powLimit = network.getProofOfWorkLimit();
        height = signet ? SIGNET_ACTIVATION_HEIGHT - 1 : COMMON_ANCHOR_HEIGHT;
        hash = signet ? SIGNET_PARENT_HASH : COMMON_ANCHOR_HASH;
        if(anchorHeaders.size() != MEDIAN_TIME_SPAN || !anchorHeaders.getLast().getHash().equals(hash)) {
            throw new VerificationException("Purity median-time seed does not reach the common checkpoint");
        }
        for(int i = 0; i < anchorHeaders.size(); i++) {
            if(i > 0 && !anchorHeaders.get(i).getPrevBlockHash().equals(anchorHeaders.get(i - 1).getHash())) {
                throw new VerificationException("Purity median-time seed has broken header linkage");
            }
            recentTimes.addLast(anchorHeaders.get(i).getTime());
        }
        if(signet) {
            int anchorIndex = asertAnchorHeight - (height - MEDIAN_TIME_SPAN + 1);
            anchorParentTime = anchorHeaders.get(anchorIndex - 1).getTime();
            referenceTarget = anchorHeaders.get(anchorIndex).getDifficultyTargetAsInteger();
            preActivationBits = anchorHeaders.getLast().getDifficultyTarget();
        } else {
            anchorParentTime = anchorHeaders.getLast().getTime();
            HeaderCheckpoints shared = Network.MAINNET.getHeaderCheckpoints();
            if(!shared.getHash(COMMON_ANCHOR_HEIGHT).equals(COMMON_ANCHOR_HASH)) {
                throw new VerificationException("Dependency does not contain the expected common checkpoint");
            }
            preActivationBits = shared.getBitsAfter(COMMON_ANCHOR_HEIGHT);
        }
    }

    private static List<BlockHeader> loadAnchor(Network network) {
        try(var input = PurityHeaderState.class.getResourceAsStream("/purity/" + network.getName() + "-anchor.hex")) {
            if(input == null) {
                throw new VerificationException("Missing Purity " + network.getName() + " median-time seed");
            }
            return new String(input.readAllBytes(), StandardCharsets.US_ASCII).lines()
                    .map(line -> new BlockHeader(Utils.hexToBytes(line))).toList();
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void add(BlockHeader header) {
        int nextHeight = height + 1;
        if(!header.getPrevBlockHash().equals(hash)) {
            throw new VerificationException("Header at height " + nextHeight + " does not link to the previous header");
        }
        if(nextHeight == activationHeight && !header.getHash().equals(activationHash)) {
            throw new VerificationException("Header does not match the Purity activation block at height " + nextHeight);
        }
        List<Long> times = new ArrayList<>(recentTimes);
        times.sort(Long::compare);
        if(header.getTime() <= times.get(MEDIAN_TIME_SPAN / 2)) {
            throw new VerificationException("Header at height " + nextHeight + " is at or before median time past");
        }
        if(header.getTime() > System.currentTimeMillis() / 1000 + 2 * 60 * 60) {
            throw new VerificationException("Header at height " + nextHeight + " is too far in the future");
        }
        long requiredBits = nextHeight < activationHeight ? preActivationBits : Utils.encodeCompactBits(
                calculateTarget(referenceTarget, recentTimes.getLast() - anchorParentTime, height - asertAnchorHeight, powLimit));
        long claimedBits = header.getDifficultyTarget();
        BigInteger target = header.getDifficultyTargetAsInteger();
        //Core permits a harder activation target, but the activation hash above still identifies exactly one block.
        boolean validDifficulty = nextHeight == activationHeight
                ? target.signum() > 0 && target.compareTo(Utils.decodeCompactBits(requiredBits)) <= 0
                : claimedBits == requiredBits;
        if(!validDifficulty) {
            throw new VerificationException("Header at height " + nextHeight + " has difficulty target " + Long.toHexString(claimedBits)
                    + " but the Purity chain requires " + Long.toHexString(requiredBits));
        }
        if(target.signum() <= 0 || target.compareTo(powLimit) > 0 || header.getHash().toBigInteger().compareTo(target) > 0) {
            throw new VerificationException("Header at height " + nextHeight + " does not meet its required proof of work target");
        }
        if(nextHeight == asertAnchorHeight) {
            referenceTarget = target;
        }
        height = nextHeight;
        hash = header.getHash();
        chainWork = chainWork.add(HeaderChainState.getWork(claimedBits));
        recentTimes.removeFirst();
        recentTimes.addLast(header.getTime());
    }

    //Integer aserti3 from Bitcoin Purity Core pow.cpp (MIT), derived from Bitcoin Cash. Signed division truncates toward zero.
    static BigInteger calculateTarget(BigInteger referenceTarget, long timeDiff, long heightDiff) {
        return calculateTarget(referenceTarget, timeDiff, heightDiff, POW_LIMIT);
    }

    static BigInteger calculateTarget(BigInteger referenceTarget, long timeDiff, long heightDiff, BigInteger powLimit) {
        long exponent = ((timeDiff - 600 * (heightDiff + 1)) * 65536) / 86400;
        int shifts = Math.toIntExact(exponent >> 16);
        BigInteger fraction = BigInteger.valueOf(exponent & 0xffff);
        BigInteger polynomial = BigInteger.valueOf(195766423245049L).multiply(fraction)
                .add(BigInteger.valueOf(971821376).multiply(fraction.pow(2)))
                .add(BigInteger.valueOf(5127).multiply(fraction.pow(3)))
                .add(BigInteger.ONE.shiftLeft(47));
        BigInteger factor = BigInteger.valueOf(65536).add(polynomial.shiftRight(48));
        BigInteger target = referenceTarget.multiply(factor);
        shifts -= 16;
        if(shifts > 0 && target.bitLength() + shifts > 256) {
            return powLimit;
        }
        target = target.shiftLeft(shifts);
        return target.max(BigInteger.ONE).min(powLimit);
    }

    @Override public synchronized int getHeight() { return height; }
    @Override public synchronized Sha256Hash getHash() { return hash; }
    @Override public synchronized BigInteger getChainWork() { return chainWork; }
}
