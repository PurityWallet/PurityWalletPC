package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.TableType;
import com.sparrowwallet.sparrow.wallet.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.net.LegacyUtxoChecker;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableColumn;

import java.util.Comparator;

public class UtxosTreeTable extends CoinTreeTable {
    public void initialize(WalletUtxosEntry rootEntry) {
        getStyleClass().add("utxos-treetable");
        setUnitFormat(rootEntry.getWallet());

        updateAll(rootEntry);
        setShowRoot(false);

        TreeTableColumn<Entry, Entry> dateCol = new TreeTableColumn<>("Date");
        dateCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Entry> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        dateCol.setCellFactory(p -> new DateCell());
        dateCol.setSortable(true);
        dateCol.setComparator(dateCol.getComparator().reversed());
        getColumns().add(dateCol);

        TreeTableColumn<Entry, Entry> outputCol = new TreeTableColumn<>("Output");
        outputCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Entry> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        outputCol.setCellFactory(p -> new EntryCell());
        outputCol.setSortable(true);
        outputCol.setComparator((o1, o2) -> {
            UtxoEntry entry1 = (UtxoEntry)o1;
            UtxoEntry entry2 = (UtxoEntry)o2;
            int hashCompare = entry1.getHashIndex().getHash().toString().compareTo(entry2.getHashIndex().getHash().toString());
            if(hashCompare != 0) {
                return hashCompare;
            }

            return (int)(entry1.getHashIndex().getIndex() - entry2.getHashIndex().getIndex());
        });
        getColumns().add(outputCol);

        if(rootEntry.getWallet().isWhirlpoolMixWallet()) {
            TreeTableColumn<Entry, UtxoEntry.MixStatus> mixStatusCol = new TreeTableColumn<>("Mixes");
            mixStatusCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, UtxoEntry.MixStatus> param) -> {
                return ((UtxoEntry)param.getValue().getValue()).mixStatusProperty();
            });
            mixStatusCol.setCellFactory(p -> new MixStatusCell());
            mixStatusCol.setSortable(true);
            mixStatusCol.setComparator(Comparator.comparingInt(UtxoEntry.MixStatus::getMixesDone));
            getColumns().add(mixStatusCol);
        } else {
            TreeTableColumn<Entry, UtxoEntry.AddressStatus> addressCol = new TreeTableColumn<>("Address");
            addressCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, UtxoEntry.AddressStatus> param) -> {
                return ((UtxoEntry)param.getValue().getValue()).addressStatusProperty();
            });
            addressCol.setCellFactory(p -> {
                AddressCell addressCell = new AddressCell();
                addressCell.setSkin(new AddressTreeTableCellSkin<>(addressCell));
                return addressCell;
            });
            addressCol.setSortable(true);
            addressCol.setComparator(Comparator.comparing(o -> o.getAddress().toString()));
            getColumns().add(addressCol);
        }

        TreeTableColumn<Entry, String> labelCol = new TreeTableColumn<>("Label");
        labelCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, String> param) -> {
            return param.getValue().getValue().labelProperty();
        });
        labelCol.setCellFactory(p -> new LabelCell());
        labelCol.setSortable(true);
        getColumns().add(labelCol);

        TreeTableColumn<Entry, Number> amountCol = new TreeTableColumn<>("Value");
        amountCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Number> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue());
        });
        amountCol.setCellFactory(p -> new CoinCell());
        amountCol.setSortable(true);
        getColumns().add(amountCol);
        setTreeColumn(amountCol);

        if(Network.get() == Network.MAINNET) {
            TreeTableColumn<Entry, LegacyUtxoChecker.Status> legacyCol = new TreeTableColumn<>("Legacy chain");
            legacyCol.setCellValueFactory(param -> ((UtxoEntry)param.getValue().getValue()).legacyStatusProperty());
            legacyCol.setCellFactory(column -> new TreeTableCell<>() {
                @Override
                protected void updateItem(LegacyUtxoChecker.Status status, boolean empty) {
                    super.updateItem(status, empty);
                    getStyleClass().removeAll("legacy-shared", "legacy-unknown");
                    setText(null);
                    setTooltip(null);
                    if(!empty && status != null) {
                        setText(switch(status) {
                            case SHARED -> "⚠ Replay risk";
                            case ABSENT -> "Not shared";
                            case UNKNOWN -> "⚠ Unknown";
                            case CHECKING -> "Checking...";
                        });
                        setTooltip(new Tooltip(switch(status) {
                            case SHARED -> "Matching unspent output exists on the legacy Bitcoin chain. Spending it may also spend your legacy-chain coins.";
                            case ABSENT -> "No matching unspent output was found by the legacy-chain service at the time of this check.";
                            case UNKNOWN -> "Legacy-chain check unavailable. Replay risk is unknown.";
                            case CHECKING -> "Checking the output on the legacy Bitcoin chain...";
                        }));
                        if(status == LegacyUtxoChecker.Status.SHARED || status == LegacyUtxoChecker.Status.UNKNOWN) {
                            getStyleClass().add(status == LegacyUtxoChecker.Status.SHARED ? "legacy-shared" : "legacy-unknown");
                        }
                    }
                }
            });
            legacyCol.setMinWidth(125);
            getColumns().add(legacyCol);
        }

        setPlaceholder(getDefaultPlaceholder(rootEntry.getWallet()));
        setEditable(true);
        setupColumnWidths();
        setupColumnSort(getColumns().indexOf(amountCol), TreeTableColumn.SortType.DESCENDING);

        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    public void updateAll(WalletUtxosEntry rootEntry) {
        setUnitFormat(rootEntry.getWallet());

        RecursiveTreeItem<Entry> rootItem = new RecursiveTreeItem<>(rootEntry, Entry::getChildren);
        setRoot(rootItem);
        rootItem.setExpanded(true);

        resetSortColumn();
    }

    public void updateHistory() {
        setUnitFormat(getRoot().getValue().getWallet());

        //Utxo entries should have already been updated, so only a resort required
        if(!getRoot().getChildren().isEmpty()) {
            sort();
            resetSortColumn();
        }
    }

    public void updateLabel(Entry entry) {
        Entry rootEntry = getRoot().getValue();
        rootEntry.updateLabel(entry);
    }
}
