// Modified for PurityWallet on 2026-10-27; see LICENSE and NOTICE.
package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.sparrow.io.Server;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum PublicElectrumServer {
    PURITY_MAINNET("Bitcoin Purity", "ssl://electrum.bitcoinpurity.org:50002", Network.MAINNET);

    PublicElectrumServer(String name, String url, Network network) {
        this(name, url, network, List.of(PolicyType.SINGLE_HD, PolicyType.MULTI_HD));
    }

    PublicElectrumServer(String name, String url, Network network, List<PolicyType> supportedPolicyTypes) {
        this.server = new Server(url, name);
        this.network = network;
        this.supportedPolicyTypes = supportedPolicyTypes;
    }

    public static final List<Network> SUPPORTED_NETWORKS = List.of(Network.MAINNET);

    private final Server server;
    private final Network network;
    private final List<PolicyType> supportedPolicyTypes;

    public Server getServer() {
        return server;
    }

    public String getUrl() {
        return server.getUrl();
    }

    public Network getNetwork() {
        return network;
    }

    public boolean isSupportedPolicyType(PolicyType policyType) {
        return supportedPolicyTypes.contains(policyType);
    }

    public boolean supportsAllPolicyTypes(List<PolicyType> policyTypes) {
        return policyTypes.stream().allMatch(this::isSupportedPolicyType);
    }

    public static List<PublicElectrumServer> getServers() {
        return Arrays.stream(values()).filter(server -> server.network == Network.get()).collect(Collectors.toList());
    }

    public static boolean supportedNetwork() {
        return SUPPORTED_NETWORKS.contains(Network.get());
    }

    public static PublicElectrumServer fromServer(Server server) {
        for(PublicElectrumServer publicServer : values()) {
            if(publicServer.getServer().equals(server)) {
                return publicServer;
            }
        }

        return null;
    }

    public static boolean isPublicServer(HostAndPort hostAndPort) {
        for(PublicElectrumServer publicServer : values()) {
            if(publicServer.getServer().getHostAndPort().equals(hostAndPort)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return server.getAlias();
    }
}
