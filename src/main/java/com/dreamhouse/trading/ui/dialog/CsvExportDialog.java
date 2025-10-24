package com.dreamhouse.trading.ui.dialog;

import com.dreamhouse.trading.core.csv.CsvDataManager;
import com.dreamhouse.trading.util.I18n;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV 匯出對話框
 * 允許用戶匯出 K 線資料到 CSV 檔案
 */
public class CsvExportDialog extends JDialog {
    
    private final List<org.ta4j.core.Bar> data;
    private boolean confirmed = false;
    
    private JTextField filePathField;
    private JCheckBox includeHeaderCheckbox;
    private JComboBox<String> dateFormatCombo;
    private JLabel dataCountLabel;
    
    public CsvExportDialog(Frame owner, List<org.ta4j.core.Bar> data) {
        super(owner, I18n.get("dialog.csv.export.title"), true);
        this.data = data;
        initComponents();
        setLocationRelativeTo(owner);
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // 主面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // 資料資訊面板
        mainPanel.add(createInfoPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        
        // 檔案選擇面板
        mainPanel.add(createFilePanel());
        mainPanel.add(Box.createVerticalStrut(10));
        
        // 選項面板
        mainPanel.add(createOptionsPanel());
        
        add(mainPanel, BorderLayout.CENTER);
        
        // 按鈕面板
        add(createButtonPanel(), BorderLayout.SOUTH);
        
        pack();
        setMinimumSize(new Dimension(500, 280));
    }
    
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(I18n.get("dialog.csv.export.data.info")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // 資料筆數
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel(I18n.get("dialog.csv.export.data.count") + ":"), gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        dataCountLabel = new JLabel(String.valueOf(data.size()));
        dataCountLabel.setFont(dataCountLabel.getFont().deriveFont(Font.BOLD));
        panel.add(dataCountLabel, gbc);
        
        // 時間範圍
        if (!data.isEmpty()) {
            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
            panel.add(new JLabel(I18n.get("dialog.csv.export.time.range") + ":"), gbc);
            
            gbc.gridx = 1; gbc.weightx = 1;
            org.ta4j.core.Bar firstBar = data.get(0);
            org.ta4j.core.Bar lastBar = data.get(data.size() - 1);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            String timeRange = firstBar.getBeginTime().format(formatter) + " ~ " + 
                              lastBar.getEndTime().format(formatter);
            JLabel timeRangeLabel = new JLabel(timeRange);
            panel.add(timeRangeLabel, gbc);
        }
        
        return panel;
    }
    
    private JPanel createFilePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(I18n.get("dialog.csv.export.file")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel(I18n.get("dialog.csv.export.file.path") + ":"), gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        filePathField = new JTextField();
        filePathField.setEditable(false);
        panel.add(filePathField, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0;
        JButton browseButton = new JButton(I18n.get("dialog.csv.browse"));
        browseButton.addActionListener(e -> browseFile());
        panel.add(browseButton, gbc);
        
        return panel;
    }
    
    private JPanel createOptionsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(I18n.get("dialog.csv.export.options")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        
        // 包含標題列
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        includeHeaderCheckbox = new JCheckBox(I18n.get("dialog.csv.export.include.header"), true);
        panel.add(includeHeaderCheckbox, gbc);
        
        // 日期格式
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0;
        panel.add(new JLabel(I18n.get("dialog.csv.export.date.format") + ":"), gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        String[] dateFormats = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyyMMddHHmmss",
            "yyyyMMddHHmm"
        };
        dateFormatCombo = new JComboBox<>(dateFormats);
        panel.add(dateFormatCombo, gbc);
        
        return panel;
    }
    
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JButton exportButton = new JButton(I18n.get("dialog.csv.export.button"));
        exportButton.addActionListener(e -> performExport());
        
        JButton cancelButton = new JButton(I18n.get("dialog.cancel"));
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        
        panel.add(exportButton);
        panel.add(cancelButton);
        
        return panel;
    }
    
    private void browseFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(I18n.get("dialog.csv.export.select.file"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
        fileChooser.setSelectedFile(new File("export_data.csv"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            // 確保副檔名是 .csv
            if (!selectedFile.getName().toLowerCase().endsWith(".csv")) {
                selectedFile = new File(selectedFile.getAbsolutePath() + ".csv");
            }
            filePathField.setText(selectedFile.getAbsolutePath());
        }
    }
    
    private void performExport() {
        // 驗證檔案路徑
        String filePath = filePathField.getText().trim();
        if (filePath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                I18n.get("dialog.csv.export.error.no.file"),
                I18n.get("dialog.error"),
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 檢查資料是否為空
        if (data.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                I18n.get("dialog.csv.export.error.no.data"),
                I18n.get("dialog.error"),
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            // 執行匯出
            File outputFile = new File(filePath);
            boolean includeHeader = includeHeaderCheckbox.isSelected();
            String dateFormat = (String) dateFormatCombo.getSelectedItem();
            
            // 將 ta4j Bar 轉換為我們的 Bar 類型
            List<com.dreamhouse.trading.core.model.Bar> convertedBars = data.stream()
                .map(ta4jBar -> new com.dreamhouse.trading.core.model.Bar(
                    ta4jBar.getBeginTime().toLocalDateTime(),  // ZonedDateTime -> LocalDateTime
                    ta4jBar.getOpenPrice().doubleValue(),
                    ta4jBar.getHighPrice().doubleValue(),
                    ta4jBar.getLowPrice().doubleValue(),
                    ta4jBar.getClosePrice().doubleValue(),
                    ta4jBar.getVolume().longValue()
                ))
                .collect(Collectors.toList());
            
            CsvDataManager.exportToCsv(convertedBars, outputFile, includeHeader, dateFormat);
            
            confirmed = true;
            
            // 顯示成功訊息
            JOptionPane.showMessageDialog(this,
                String.format(I18n.get("dialog.csv.export.success"), data.size(), outputFile.getAbsolutePath()),
                I18n.get("dialog.success"),
                JOptionPane.INFORMATION_MESSAGE);
            
            dispose();
            
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                I18n.get("dialog.csv.export.error") + ": " + ex.getMessage(),
                I18n.get("dialog.error"),
                JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    public String getFilePath() {
        return filePathField.getText().trim();
    }
}

