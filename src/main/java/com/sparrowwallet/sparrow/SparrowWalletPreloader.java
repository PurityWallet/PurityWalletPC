// Modified for PurityWallet on 2026-10-27; see LICENSE and NOTICE.
package com.sparrowwallet.sparrow;

import javafx.application.Preloader;
import javafx.stage.Stage;

public class SparrowWalletPreloader extends Preloader {
    @Override
    public void start(Stage stage) {
        com.sun.glass.ui.Application.GetApplication().setName("PurityWallet");
    }
}
