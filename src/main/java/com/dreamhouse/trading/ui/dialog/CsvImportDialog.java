package com.dreamhouse.trading.ui.dialog;

import com.dreamhouse.trading.core.csv.CsvDataManager;
import com.dreamhouse.trading.core.csv.CsvDataManager.CsvValidationResult;
import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.util.I18n;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * CSV 匯入對話框
 */
public class CsvImportDialog extends JDialog {
    
    private final JTextField filePathField;
    private final JCheckBox hasHeaderCheckBox;
    private final JTextArea previewArea;
    private final JTextArea validationArea;
    private final JButton validateButton;
    private final JButton importButton;
    
    private File selectedFile;
    private List<Bar> importedBars;
    private boolean confirmed = false;
    
    public CsvImportDialog(Frame owner) {
        super(owner, I18n.get("dialog.csv.import.title"), true);
        setLayout(new MigLayout("wrap 1, fill, insets 15", "[grow,fill]", "[][][][grow][grow][]"));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(700, 650);
        setLocationRelativeTo(owner);
        
        // 檔案選擇區
        JPanel filePanel = new JPanel(new MigLayout("wrap 3, insets 0", "[grow,fill][]", "[]"));
        filePanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            I18n.get("dialog.csv.import.file.section"),
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        filePathField = new JTextField();
        filePathField.setEditable(false);
        JButton browseButton = new JButton(I18n.get("dialog.csv.import.browse"));
        browseButton.addActionListener(e -> browseFile());
        
        hasHeaderCheckBox = new JCheckBox(I18n.get("dialog.csv.import.has.header"), true);
        
        filePanel.add(new JLabel(I18n.get("dialog.csv.import.file") + ":"), "split 3");
        filePanel.add(filePathField, "growx");
        filePanel.add(browseButton);
        filePanel.add(hasHeaderCheckBox, "span 3");
        
        add(filePanel, "growx");
        
        // 預覽區
        JPanel previewPanel = new JPanel(new MigLayout("fill, insets 0"));
        previewPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            I18n.get("dialog.csv.import.preview"),
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        previewArea.setLineWrap(false);
        JScrollPane previewScroll = new JScrollPane(previewArea);
        
        previewPanel.add(previewScroll, "grow");
        add(previewPanel, "grow, height 150:200:250");
        
        // 驗證區
        JPanel validationPanel = new JPanel(new MigLayout("fill, insets 0"));
        validationPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            I18n.get("dialog.csv.import.validation"),
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        validationArea = new JTextArea();
        validationArea.setEditable(false);
        validationArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane validationScroll = new JScrollPane(validationArea);
        
        validationPanel.add(validationScroll, "grow");
        add(validationPanel, "grow, height 120:150:200");
        
        // 按鈕區
        JPanel buttonPanel = new JPanel(new MigLayout("insets 0"));
        
        validateButton = new JButton(I18n.get("dialog.csv.import.validate"));
        validateButton.setEnabled(false);
        validateButton.addActionListener(e -> validateFile());
        
        importButton = new JButton(I18n.get("dialog.csv.import.import"));
        importButton.setEnabled(false);
        importButton.addActionListener(e -> performImport());
        
        JButton cancelButton = new JButton(I18n.get("button.cancel"));
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(validateButton);
        buttonPanel.add(importButton);
        buttonPanel.add(cancelButton);
        
        add(buttonPanel, "right");
    }
    
    private void browseFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".csv");
            }
            
            @Override
            public String getDescription() {
                return "CSV Files (*.csv)";
            }
        });
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = fileChooser.getSelectedFile();
            filePathField.setText(selectedFile.getAbsolutePath());
            validateButton.setEnabled(true);
            importButton.setEnabled(false);
            
            // 顯示預覽
            showPreview();
        }
    }
    
    private void showPreview() {
        if (selectedFile == null) {
            return;
        }
        
        StringBuilder preview = new StringBuilder();
        preview.append(String.format("File: %s%n", selectedFile.getName()));
        preview.append(String.format("Size: %,d bytes%n%n", selectedFile.length()));
        preview.append("First 10 lines:%n");
        preview.append("─".repeat(60)).append("\n");
        
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(selectedFile))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 10) {
                preview.append(line).append("\n");
                count++;
            }
        } catch (Exception e) {
            preview.append("Error reading file: ").append(e.getMessage());
        }
        
        previewArea.setText(preview.toString());
        previewArea.setCaretPosition(0);
    }
    
    private void validateFile() {
        if (selectedFile == null) {
            return;
        }
        
        validationArea.setText("Validating...\n");
        
        SwingWorker<CsvValidationResult, Void> worker = new SwingWorker<>() {
            @Override
            protected CsvValidationResult doInBackground() {
                return CsvDataManager.validateCsvFile(selectedFile, hasHeaderCheckBox.isSelected());
            }
            
            @Override
            protected void done() {
                try {
                    CsvValidationResult result = get();
                    validationArea.setText(result.getSummary());
                    
                    if (result.isValid()) {
                        importButton.setEnabled(true);
                        JOptionPane.showMessageDialog(
                            CsvImportDialog.this,
                            String.format(I18n.get("dialog.csv.import.validation.success"), result.getValidRows()),
                            I18n.get("dialog.csv.import.validation"),
                            JOptionPane.INFORMATION_MESSAGE
                        );
                    } else {
                        importButton.setEnabled(false);
                        JOptionPane.showMessageDialog(
                            CsvImportDialog.this,
                            I18n.get("dialog.csv.import.validation.failed"),
                            I18n.get("dialog.csv.import.validation"),
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                } catch (Exception e) {
                    validationArea.setText("Validation error: " + e.getMessage());
                    importButton.setEnabled(false);
                }
                
                validationArea.setCaretPosition(0);
            }
        };
        
        worker.execute();
    }
    
    private void performImport() {
        if (selectedFile == null) {
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(
            this,
            I18n.get("dialog.csv.import.confirm.message"),
            I18n.get("dialog.csv.import.confirm.title"),
            JOptionPane.YES_NO_OPTION
        );
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Importing...");
        progressBar.setStringPainted(true);
        
        JDialog progressDialog = new JDialog(this, "Importing", true);
        progressDialog.setLayout(new BorderLayout(10, 10));
        progressDialog.add(new JLabel("  " + I18n.get("dialog.csv.import.importing")), BorderLayout.NORTH);
        progressDialog.add(progressBar, BorderLayout.CENTER);
        progressDialog.setSize(300, 100);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        
        SwingWorker<List<Bar>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Bar> doInBackground() throws Exception {
                return CsvDataManager.importFromCsv(selectedFile, hasHeaderCheckBox.isSelected());
            }
            
            @Override
            protected void done() {
                progressDialog.dispose();
                
                try {
                    importedBars = get();
                    confirmed = true;
                    
                    JOptionPane.showMessageDialog(
                        CsvImportDialog.this,
                        String.format(I18n.get("dialog.csv.import.success"), importedBars.size()),
                        I18n.get("dialog.csv.import.title"),
                        JOptionPane.INFORMATION_MESSAGE
                    );
                    
                    dispose();
                    
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(
                        CsvImportDialog.this,
                        I18n.get("dialog.csv.import.failed") + "\n" + e.getMessage(),
                        I18n.get("dialog.csv.import.title"),
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        };
        
        worker.execute();
        progressDialog.setVisible(true); // 阻塞直到 worker 完成
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    public List<Bar> getImportedBars() {
        return importedBars;
    }
}

