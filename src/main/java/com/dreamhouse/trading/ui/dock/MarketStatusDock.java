package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.scanner.MarketContextSnapshot;
import com.dreamhouse.trading.core.scanner.MarketMetric;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.text.DecimalFormat;

public class MarketStatusDock extends JPanel {

    private final DecimalFormat percentFmt = new DecimalFormat("0.00");
    private final DecimalFormat numberFmt = new DecimalFormat("0.00");
    private final JLabel regimeLabel = new JLabel("--");
    private final JLabel taiexLabel = new JLabel("--");
    private final JLabel tpexLabel = new JLabel("--");
    private final JLabel ruleLabel = new JLabel("--");

    public MarketStatusDock() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("大盤狀態"));

        JPanel grid = new JPanel(new GridLayout(0, 1, 4, 4));
        grid.add(regimeLabel);
        grid.add(taiexLabel);
        grid.add(tpexLabel);
        grid.add(ruleLabel);
        add(grid, BorderLayout.CENTER);
    }

    public void updateSnapshot(MarketContextSnapshot snapshot) {
        Runnable task = () -> applySnapshot(snapshot);
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void applySnapshot(MarketContextSnapshot snapshot) {
        if (snapshot == null) {
            regimeLabel.setText("市場狀態：--");
            taiexLabel.setText("TAIEX：--");
            tpexLabel.setText("TPEx：--");
            ruleLabel.setText("開倉規則：等待資料");
            return;
        }
        regimeLabel.setText("市場狀態：" + snapshot.regime().getDisplayName() + " | " + snapshot.status());
        taiexLabel.setText(formatMetric("TAIEX", snapshot.taiex()));
        tpexLabel.setText(formatMetric("TPEx", snapshot.tpex()));
        ruleLabel.setText("開倉規則：" + switch (snapshot.regime()) {
            case TREND_UP -> "標準 B 組條件";
            case RANGE -> "提高 VWAP 與量能延續門檻";
            case WEAK -> "只允許強於 VWAP、族群、大盤的股票";
            case DATA_MISSING -> "大盤資料不足，禁止自動開倉";
        });
    }

    private String formatMetric(String label, MarketMetric metric) {
        if (metric == null || !metric.hasData()) {
            return label + "：資料不足";
        }
        return label
                + "：漲跌 " + percentFmt.format(metric.returnPercent()) + "%"
                + " | Close " + numberFmt.format(metric.close())
                + " | VWAP " + numberFmt.format(metric.vwap())
                + " | slope " + percentFmt.format(metric.vwapSlopePercent()) + "%"
                + " | 量能延續 " + (metric.volumeSustain() ? "是" : "否");
    }
}
