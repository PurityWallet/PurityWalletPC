// Modified for PurityWallet on 2026-10-27; see LICENSE and NOTICE.
package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.AppServices;
import javafx.beans.NamedArg;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.StackPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.girod.javafx.svgimage.SVGImage;
import org.girod.javafx.svgimage.SVGLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class WalletModelImage extends StackPane {
    private static final Logger log = LoggerFactory.getLogger(WalletModelImage.class);

    public static final int WIDTH = 50;
    public static final int HEIGHT = 50;

    private final ObjectProperty<WalletModel> walletModelProperty = new SimpleObjectProperty<>();

    public WalletModelImage() {
        setPrefSize(WIDTH, HEIGHT);
        walletModelProperty.addListener((observable, oldValue, walletModel) -> {
            refresh(walletModel);
        });
    }

    public WalletModelImage(@NamedArg("walletModel") WalletModel walletModel) {
        this();
        walletModelProperty.set(walletModel);
    }

    public WalletModel getWalletModel() {
        return walletModelProperty.get();
    }

    public ObjectProperty<WalletModel> walletModelProperty() {
        return walletModelProperty;
    }

    public void refresh() {
        WalletModel walletModel = getWalletModel();
        refresh(walletModel);
    }

    protected void refresh(WalletModel walletModel) {
        if(walletModel == WalletModel.SPARROW) {
            ImageView logo = new ImageView(new Image(AppServices.class.getResource("/image/puritywallet-logo.png").toExternalForm()));
            logo.setFitWidth(WIDTH);
            logo.setFitHeight(HEIGHT);
            logo.setPreserveRatio(true);
            getChildren().setAll(logo);
            return;
        }

        SVGImage svgImage;
        if(AppServices.isDarkTheme()) {
            svgImage = loadSVGImage("/image/walletmodel/" + walletModel.getType() + "-invert.svg");
        } else {
            svgImage = loadSVGImage("/image/walletmodel/" + walletModel.getType() + ".svg");
        }

        if(svgImage != null) {
            getChildren().clear();
            getChildren().add(svgImage);
        }
    }

    private SVGImage loadSVGImage(String imageName) {
        try {
            URL url = AppServices.class.getResource(imageName);
            if(url != null) {
                return SVGLoader.load(url);
            }
        } catch(Exception e) {
            log.error("Could not find image " + imageName);
        }

        return null;
    }
}
