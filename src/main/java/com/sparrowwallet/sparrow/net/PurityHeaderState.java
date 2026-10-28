package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.*;

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
    private static final Sha256Hash COMMON_ANCHOR_HASH = Sha256Hash.wrap("00000000000000000000807f9dc917442a67910426d79ebb2f8aa2149327ce8a");
    private static final BigInteger POW_LIMIT = Network.MAINNET.getProofOfWorkLimit();
    private static final int MEDIAN_TIME_SPAN = 11;
    private final Deque<Long> recentTimes = new ArrayDeque<>(MEDIAN_TIME_SPAN);
    private final long anchorParentTime;
    private final long preActivationBits;
    private BigInteger referenceTarget;
    private int height = COMMON_ANCHOR_HEIGHT;
    private Sha256Hash hash = COMMON_ANCHOR_HASH;
    private BigInteger chainWork = BigInteger.ZERO;

    PurityHeaderState() {
        this(loadAnchor());
    }

    PurityHeaderState(List<BlockHeader> anchorHeaders) {
        if(anchorHeaders.size() != MEDIAN_TIME_SPAN || !anchorHeaders.getLast().getHash().equals(COMMON_ANCHOR_HASH)) {
            throw new VerificationException("Purity median-time seed does not reach the common checkpoint");
        }
        for(int i = 0; i < anchorHeaders.size(); i++) {
            if(i > 0 && !anchorHeaders.get(i).getPrevBlockHash().equals(anchorHeaders.get(i - 1).getHash())) {
                throw new VerificationException("Purity median-time seed has broken header linkage");
            }
            recentTimes.addLast(anchorHeaders.get(i).getTime());
        }
        anchorParentTime = anchorHeaders.getLast().getTime();
        HeaderCheckpoints shared = Network.MAINNET.getHeaderCheckpoints();
        if(!shared.getHash(COMMON_ANCHOR_HEIGHT).equals(COMMON_ANCHOR_HASH)) {
            throw new VerificationException("Dependency does not contain the expected common checkpoint");
        }
        preActivationBits = shared.getBitsAfter(COMMON_ANCHOR_HEIGHT);
    }

    private static List<BlockHeader> loadAnchor() {
        try(var input = PurityHeaderState.class.getResourceAsStream("/purity/mainnet-anchor.hex")) {
            if(input == null) {
                throw new VerificationException("Missing Purity mainnet median-time seed");
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
        if(nextHeight == ACTIVATION_HEIGHT && !header.getHash().equals(ACTIVATION_HASH)) {
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
        long requiredBits = nextHeight < ACTIVATION_HEIGHT ? preActivationBits : Utils.encodeCompactBits(
                calculateTarget(referenceTarget, recentTimes.getLast() - anchorParentTime, height - ASERT_ANCHOR_HEIGHT));
        long claimedBits = header.getDifficultyTarget();
        BigInteger target = header.getDifficultyTargetAsInteger();
        //Core permits a harder activation target, but the activation hash above still identifies exactly one block.
        boolean validDifficulty = nextHeight == ACTIVATION_HEIGHT
                ? target.signum() > 0 && target.compareTo(Utils.decodeCompactBits(requiredBits)) <= 0
                : claimedBits == requiredBits;
        if(!validDifficulty) {
            throw new VerificationException("Header at height " + nextHeight + " has difficulty target " + Long.toHexString(claimedBits)
                    + " but the Purity chain requires " + Long.toHexString(requiredBits));
        }
        if(target.signum() <= 0 || target.compareTo(POW_LIMIT) > 0 || header.getHash().toBigInteger().compareTo(target) > 0) {
            throw new VerificationException("Header at height " + nextHeight + " does not meet its required proof of work target");
        }
        if(nextHeight == ASERT_ANCHOR_HEIGHT) {
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
            return POW_LIMIT;
        }
        target = target.shiftLeft(shifts);
        return target.max(BigInteger.ONE).min(POW_LIMIT);
    }

    @Override public synchronized int getHeight() { return height; }
    @Override public synchronized Sha256Hash getHash() { return hash; }
    @Override public synchronized BigInteger getChainWork() { return chainWork; }
}
