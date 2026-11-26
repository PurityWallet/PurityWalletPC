package com.sparrowwallet.sparrow.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PwcConfigTest {
    @Test
    void pwcIsEnabledByDefault() {
        assertTrue(new Config().isPwcEnabled());
        assertTrue(Config.getGson().fromJson("{}", Config.class).isPwcEnabled());
    }

    @Test
    void disabledPwcSurvivesReload() {
        assertFalse(Config.getGson().fromJson("{\"pwcEnabled\":false}", Config.class).isPwcEnabled());
    }
}
