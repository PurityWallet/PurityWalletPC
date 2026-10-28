// Modified for PurityWallet on 2026-10-27; see LICENSE and NOTICE.
package com.sparrowwallet.sparrow.net;

public enum ServerType {
    BITCOIN_CORE("Bitcoin Purity Core"), ELECTRUM_SERVER("Private Electrum"), PUBLIC_ELECTRUM_SERVER("Public Electrum");

    private final String name;

    ServerType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
