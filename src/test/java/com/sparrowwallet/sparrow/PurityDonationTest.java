package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.Network;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PurityDonationTest {
    @Test
    void donationHasNoAmountAndCannotInjectOtherPaymentParameters() throws Exception {
        Network.set(Network.MAINNET);
        var uri = AppServices.donationURI("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");
        assertEquals("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", uri.getAddress().toString());
        assertNull(uri.getAmount());
        assertThrows(Exception.class, () -> AppServices.donationURI("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa?amount=1"));
    }

    @Test
    void shippedPlaceholderIsNotPayable() {
        assertThrows(Exception.class, () -> AppServices.donationURI(AppServices.PURITY_WALLET_DONATION_ADDRESS));
    }

    @Test
    void retainsTheReleasedPurityWalletIdentity() {
        assertEquals("PurityWallet", SparrowWallet.APP_NAME);
        assertEquals("PurityWallet", SparrowWallet.STORAGE_NAME);
        assertEquals("puritywallet.home", SparrowWallet.APP_HOME_PROPERTY);
        assertEquals("puritywallet", SparrowWallet.APP_ID);
    }
}
