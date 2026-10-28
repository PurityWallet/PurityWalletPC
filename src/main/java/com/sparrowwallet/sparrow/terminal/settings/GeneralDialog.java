// Modified for PurityWallet on 2026-10-27; see LICENSE and NOTICE.
package com.sparrowwallet.sparrow.terminal.settings;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.event.BitcoinUnitChangedEvent;
import com.sparrowwallet.sparrow.event.UnitFormatChangedEvent;
import com.sparrowwallet.sparrow.io.Config;
import javafx.application.Platform;

import java.util.List;

public class GeneralDialog extends DialogWindow {
    private final ComboBox<BitcoinUnit> bitcoinUnit;
    private final ComboBox<String> unitFormat;

    public GeneralDialog() {
        super("General Settings");

        setHints(List.of(Hint.CENTERED));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(5));

        mainPanel.addComponent(new Label("Bitcoin Unit"));
        bitcoinUnit = new ComboBox<>(BitcoinUnit.values());
        mainPanel.addComponent(bitcoinUnit);

        mainPanel.addComponent(new Label("Unit Format"));
        unitFormat = new ComboBox<>("1,234.56", "1.235,56");
        mainPanel.addComponent(unitFormat);

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        bitcoinUnit.setSelectedItem(Config.get().getBitcoinUnit() == null ? BitcoinUnit.AUTO : Config.get().getBitcoinUnit());
        unitFormat.setSelectedIndex(Config.get().getUnitFormat() == UnitFormat.COMMA ? 1 : 0);

        bitcoinUnit.addListener((int selectedIndex, int previousSelection, boolean changedByUserInteraction) -> {
            BitcoinUnit newValue = bitcoinUnit.getSelectedItem();
            Config.get().setBitcoinUnit(newValue);
            Platform.runLater(() -> {
                EventManager.get().post(new BitcoinUnitChangedEvent(newValue));
            });
        });

        unitFormat.addListener((int selectedIndex, int previousSelection, boolean changedByUserInteraction) -> {
            UnitFormat format = selectedIndex == 1 ? UnitFormat.COMMA : UnitFormat.DOT;
            Config.get().setUnitFormat(format);
            Platform.runLater(() -> {
                EventManager.get().post(new UnitFormatChangedEvent(format));
            });
        });

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Done", this::onDone).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false)));

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);
    }

    private void onDone() {
        close();
    }

}
