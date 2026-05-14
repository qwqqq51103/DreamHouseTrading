package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.MarketDataCollectorRepository;
import com.dreamhouse.trading.core.scanner.IndustryStrength;
import com.dreamhouse.trading.core.scanner.MarketContextSnapshot;
import com.dreamhouse.trading.ui.UIAutoScaler;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class IndustryDistributionDock extends JPanel {

    private static final String ALL_INDUSTRIES = "全部大類";

    private final IndustryTableModel industryTableModel = new IndustryTableModel();
    private final IndustryStockTableModel stockTableModel = new IndustryStockTableModel();
    private final JTable industryTable = new JTable(industryTableModel);
    private final JTable stockTable = new JTable(stockTableModel);
    private final JComboBox<String> industryFilter = new JComboBox<>(new String[]{ALL_INDUSTRIES});

    private List<IndustryStrength> allIndustryRows = new ArrayList<>();
    private List<MarketDataCollectorRepository.IndustryStockInfo> allStockRows = new ArrayList<>();
    private Set<String> watchlistSymbols = Set.of();
    private Consumer<String> onAddIndustrySymbols;
    private Consumer<String> onRemoveIndustrySymbols;
    private Consumer<List<String>> onAddSymbols;

    public IndustryDistributionDock() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("產業分布"));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("大類"));
        industryFilter.setPrototypeDisplayValue("AI/伺服器/電腦週邊");
        industryFilter.addActionListener(e -> applyFilter());
        topPanel.add(industryFilter);
        add(topPanel, BorderLayout.NORTH);

        industryTable.setFillsViewportHeight(true);
        industryTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        industryTable.setDefaultRenderer(Object.class, new IndustryRenderer());
        industryTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectIndustryFromTable();
            }
        });

        stockTable.setFillsViewportHeight(true);
        stockTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        stockTable.setDefaultRenderer(Object.class, new StockRenderer());

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(industryTable),
                new JScrollPane(stockTable));
        splitPane.setResizeWeight(0.35);
        add(splitPane, BorderLayout.CENTER);

        JButton addSelectedButton = new JButton("加入選取股票");
        addSelectedButton.setToolTipText("將下方勾選或目前選取的股票加入觀察清單，並同步 MarketDataCollector symbols.properties");
        addSelectedButton.addActionListener(e -> addSelectedSymbols());

        JButton addIndustryButton = new JButton("加入整個族群");
        addIndustryButton.setToolTipText("將目前大類底下的股票加入觀察清單，並同步 MarketDataCollector symbols.properties");
        addIndustryButton.addActionListener(e -> addSelectedIndustry());

        JButton removeIndustryButton = new JButton("刪除整個族群");
        removeIndustryButton.setToolTipText("從觀察清單與 MarketDataCollector symbols.properties 移除目前選取大類的股票");
        removeIndustryButton.addActionListener(e -> removeSelectedIndustry());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(addSelectedButton);
        buttonPanel.add(addIndustryButton);
        buttonPanel.add(removeIndustryButton);
        add(buttonPanel, BorderLayout.SOUTH);

        UIAutoScaler.install(this);
    }

    public void setOnAddIndustrySymbols(Consumer<String> callback) {
        this.onAddIndustrySymbols = callback;
    }

    public void setOnRemoveIndustrySymbols(Consumer<String> callback) {
        this.onRemoveIndustrySymbols = callback;
    }

    public void setOnAddSymbols(Consumer<List<String>> callback) {
        this.onAddSymbols = callback;
    }

    public void updateIndustryRows(List<IndustryStrength> rows) {
        updateIndustryData(rows, allStockRows, watchlistSymbols);
    }

    public void updateIndustryData(
            List<IndustryStrength> industryRows,
            List<MarketDataCollectorRepository.IndustryStockInfo> stockRows,
            Set<String> currentWatchlistSymbols) {
        Runnable task = () -> setRows(industryRows, stockRows, currentWatchlistSymbols);
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    public void updateSnapshot(MarketContextSnapshot snapshot) {
        List<IndustryStrength> rows = snapshot == null || snapshot.industries() == null
                ? List.of()
                : snapshot.industries().values().stream()
                        .filter(IndustryStrength::hasData)
                        .sorted(Comparator.comparingDouble(IndustryStrength::score).reversed())
                        .toList();
        updateIndustryRows(rows);
    }

    private void setRows(
            List<IndustryStrength> industryRows,
            List<MarketDataCollectorRepository.IndustryStockInfo> stockRows,
            Set<String> currentWatchlistSymbols) {
        allIndustryRows = mergeIndustryRowsWithStockRows(industryRows, stockRows);
        allIndustryRows.sort(Comparator.comparingDouble(IndustryStrength::score).reversed()
                .thenComparing(IndustryStrength::industry));
        allStockRows = stockRows != null ? new ArrayList<>(stockRows) : new ArrayList<>();
        allStockRows.sort(Comparator.comparing(MarketDataCollectorRepository.IndustryStockInfo::industryGroup)
                .thenComparing(MarketDataCollectorRepository.IndustryStockInfo::symbol));
        watchlistSymbols = normalizeSymbols(currentWatchlistSymbols);
        rebuildFilterOptions();
        applyFilter();
    }

    private List<IndustryStrength> mergeIndustryRowsWithStockRows(
            List<IndustryStrength> industryRows,
            List<MarketDataCollectorRepository.IndustryStockInfo> stockRows) {
        Map<String, IndustryStrength> metricsByIndustry = new LinkedHashMap<>();
        if (industryRows != null) {
            for (IndustryStrength row : industryRows) {
                if (row != null && row.industry() != null && !row.industry().isBlank()) {
                    metricsByIndustry.put(row.industry(), row);
                }
            }
        }
        Map<String, Set<String>> symbolsByIndustry = new LinkedHashMap<>();
        if (stockRows != null) {
            for (MarketDataCollectorRepository.IndustryStockInfo row : stockRows) {
                if (row == null || row.industryGroup() == null || row.industryGroup().isBlank()) {
                    continue;
                }
                String symbol = normalizeSymbol(row.symbol());
                if (symbol.isBlank()) {
                    continue;
                }
                symbolsByIndustry.computeIfAbsent(row.industryGroup(), ignored -> new LinkedHashSet<>())
                        .add(symbol);
            }
        }
        if (symbolsByIndustry.isEmpty()) {
            return industryRows != null ? new ArrayList<>(industryRows) : new ArrayList<>();
        }
        List<IndustryStrength> merged = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : symbolsByIndustry.entrySet()) {
            IndustryStrength metrics = metricsByIndustry.get(entry.getKey());
            int symbolCount = entry.getValue().size();
            if (metrics == null) {
                merged.add(new IndustryStrength(
                        entry.getKey(),
                        0.0,
                        0.0,
                        0,
                        0,
                        0.0,
                        symbolCount,
                        0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        "SQL產業成分股"));
            } else {
                merged.add(new IndustryStrength(
                        metrics.industry(),
                        metrics.averageReturnPercent(),
                        metrics.averageVolume(),
                        metrics.symbolCount(),
                        Math.min(metrics.strongSymbolCount(), Math.max(metrics.dataSymbolCount(), metrics.symbolCount())),
                        metrics.score(),
                        Math.max(symbolCount, metrics.totalSymbolCount()),
                        metrics.dataSymbolCount(),
                        metrics.coveragePercent(),
                        metrics.relativeBenchmarkPercent(),
                        metrics.vwapPassPercent(),
                        metrics.volumeSustainPercent(),
                        metrics.source()));
            }
        }
        return merged;
    }

    private Set<String> normalizeSymbols(Set<String> symbols) {
        Set<String> normalized = new LinkedHashSet<>();
        if (symbols != null) {
            for (String symbol : symbols) {
                normalized.add(normalizeSymbol(symbol));
            }
        }
        return normalized;
    }

    private void rebuildFilterOptions() {
        Object selected = industryFilter.getSelectedItem();
        Set<String> values = new LinkedHashSet<>();
        values.add(ALL_INDUSTRIES);
        for (IndustryStrength row : allIndustryRows) {
            if (row.industry() != null && !row.industry().isBlank()) {
                values.add(row.industry());
            }
        }
        for (MarketDataCollectorRepository.IndustryStockInfo row : allStockRows) {
            if (row.industryGroup() != null && !row.industryGroup().isBlank()) {
                values.add(row.industryGroup());
            }
        }
        industryFilter.setModel(new DefaultComboBoxModel<>(values.toArray(String[]::new)));
        if (selected != null && values.contains(selected.toString())) {
            industryFilter.setSelectedItem(selected);
        }
    }

    private void applyFilter() {
        String selected = String.valueOf(industryFilter.getSelectedItem());
        boolean showAll = selected == null || selected.isBlank() || ALL_INDUSTRIES.equals(selected);
        industryTableModel.setRows(showAll
                ? allIndustryRows
                : allIndustryRows.stream().filter(row -> selected.equals(row.industry())).toList());
        stockTableModel.setRows(showAll
                ? allStockRows
                : allStockRows.stream().filter(row -> selected.equals(row.industryGroup())).toList(),
                watchlistSymbols);
    }

    private void selectIndustryFromTable() {
        int selectedRow = industryTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        IndustryStrength row = industryTableModel.getRow(industryTable.convertRowIndexToModel(selectedRow));
        if (row != null && row.industry() != null && !row.industry().isBlank()) {
            industryFilter.setSelectedItem(row.industry());
        }
    }

    private void addSelectedSymbols() {
        List<String> symbols = stockTableModel.selectedSymbols();
        if (symbols.isEmpty()) {
            int row = stockTable.getSelectedRow();
            if (row >= 0) {
                symbols = List.of(stockTableModel.getSymbol(stockTable.convertRowIndexToModel(row)));
            }
        }
        symbols = symbols.stream().filter(symbol -> symbol != null && !symbol.isBlank()).distinct().toList();
        if (symbols.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請先勾選或選取要加入的股票。", "產業選股", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (onAddSymbols != null) {
            onAddSymbols.accept(symbols);
        }
    }

    private void addSelectedIndustry() {
        String industry = String.valueOf(industryFilter.getSelectedItem());
        if (industry == null || industry.isBlank() || ALL_INDUSTRIES.equals(industry)) {
            JOptionPane.showMessageDialog(this, "請先用選單或表格選取一個產業大類。", "產業選股", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (onAddIndustrySymbols != null) {
            onAddIndustrySymbols.accept(industry);
        }
    }

    private void removeSelectedIndustry() {
        String industry = String.valueOf(industryFilter.getSelectedItem());
        if (industry == null || industry.isBlank() || ALL_INDUSTRIES.equals(industry)) {
            JOptionPane.showMessageDialog(this, "請先選擇一個產業大類。", "刪除族群", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        long watchlistCount = allStockRows.stream()
                .filter(row -> industry.equals(row.industryGroup()))
                .map(row -> normalizeSymbol(row.symbol()))
                .filter(watchlistSymbols::contains)
                .count();
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "確定要從觀察清單與 MarketDataCollector 移除「" + industry + "」族群？\n"
                        + "目前觀察清單內符合此族群約 " + watchlistCount + " 檔。",
                "刪除族群",
                JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION && onRemoveIndustrySymbols != null) {
            onRemoveIndustrySymbols.accept(industry);
        }
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase();
    }

    private static class IndustryTableModel extends AbstractTableModel {
        private final String[] columns = {
                "產業大類",
                "平均漲跌%",
                "相對大盤%",
                "平均量",
                "強勢股數",
                "有資料/總檔",
                "覆蓋率%",
                "VWAP通過%",
                "量能延續%",
                "強度分數",
                "來源"};
        private List<IndustryStrength> rows = new ArrayList<>();

        void setRows(List<IndustryStrength> rows) {
            this.rows = rows != null ? new ArrayList<>(rows) : new ArrayList<>();
            fireTableDataChanged();
        }

        IndustryStrength getRow(int row) {
            return row >= 0 && row < rows.size() ? rows.get(row) : null;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            IndustryStrength row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.industry();
                case 1 -> row.averageReturnPercent();
                case 2 -> row.relativeBenchmarkPercent();
                case 3 -> row.averageVolume();
                case 4 -> row.strongSymbolCount();
                case 5 -> row.dataSymbolCount() + "/" + row.totalSymbolCount();
                case 6 -> row.coveragePercent();
                case 7 -> row.vwapPassPercent();
                case 8 -> row.volumeSustainPercent();
                case 9 -> row.score();
                case 10 -> row.source();
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 1, 2, 3, 6, 7, 8, 9 -> Double.class;
                case 4 -> Integer.class;
                default -> Object.class;
            };
        }
    }

    private static class IndustryStockTableModel extends AbstractTableModel {
        private final String[] columns = {"選取", "代碼", "中文名稱", "大類", "產業", "子產業", "產業鏈", "觀察清單"};
        private final List<StockRow> rows = new ArrayList<>();

        void setRows(List<MarketDataCollectorRepository.IndustryStockInfo> stockRows, Set<String> watchlistSymbols) {
            rows.clear();
            if (stockRows != null) {
                for (MarketDataCollectorRepository.IndustryStockInfo row : stockRows) {
                    rows.add(new StockRow(row, watchlistSymbols.contains(normalizeSymbol(row.symbol()))));
                }
            }
            fireTableDataChanged();
        }

        List<String> selectedSymbols() {
            return rows.stream()
                    .filter(row -> row.selected)
                    .map(row -> row.info.symbol())
                    .toList();
        }

        String getSymbol(int row) {
            return row >= 0 && row < rows.size() ? rows.get(row).info.symbol() : "";
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            StockRow row = rows.get(rowIndex);
            MarketDataCollectorRepository.IndustryStockInfo info = row.info;
            return switch (columnIndex) {
                case 0 -> row.selected;
                case 1 -> info.symbol();
                case 2 -> info.stockName() == null || info.stockName().isBlank() ? "--" : info.stockName();
                case 3 -> info.industryGroup();
                case 4 -> info.industry();
                case 5 -> info.subIndustry();
                case 6 -> info.chainName();
                case 7 -> row.inWatchlist ? "已加入" : "";
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex == 0 && rowIndex >= 0 && rowIndex < rows.size()) {
                rows.get(rowIndex).selected = Boolean.TRUE.equals(value);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : Object.class;
        }
    }

    private static class StockRow {
        private final MarketDataCollectorRepository.IndustryStockInfo info;
        private final boolean inWatchlist;
        private boolean selected;

        private StockRow(MarketDataCollectorRepository.IndustryStockInfo info, boolean inWatchlist) {
            this.info = info;
            this.inWatchlist = inWatchlist;
        }
    }

    private static class IndustryRenderer extends DefaultTableCellRenderer {
        private final DecimalFormat pctFmt = new DecimalFormat("0.00");
        private final DecimalFormat numberFmt = new DecimalFormat("#,##0");
        private final DecimalFormat scoreFmt = new DecimalFormat("0.00");

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof Double number) {
                if (column == 1 || column == 2 || column == 6 || column == 7 || column == 8) {
                    setText(pctFmt.format(number) + "%");
                } else if (column == 3) {
                    setText(numberFmt.format(number));
                } else if (column == 9) {
                    setText(scoreFmt.format(number));
                }
            }
            return component;
        }
    }

    private static class StockRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setToolTipText(value == null ? "" : value.toString());
            return component;
        }
    }
}
