// Modified for PurityWallet on 2026-10-27; see LICENSE and NOTICE.
package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NetworkInfo(int version, String subversion, boolean networkactive) {
    public boolean supportsPurity() {
        return version >= 10000 && subversion != null && subversion.contains("/Purity:");
    }
}
