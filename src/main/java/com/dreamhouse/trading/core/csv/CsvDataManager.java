package com.dreamhouse.trading.core.csv;

import com.dreamhouse.trading.core.model.Bar;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * CSV 數據管理器
 * 負責匯入/匯出 K 線數據，支援多種格式
 */
public class CsvDataManager {
    
    private static final DateTimeFormatter[] SUPPORTED_FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };
    
    /**
     * 從 CSV 檔案匯入 K 線數據
     * 
     * @param file CSV 檔案
     * @param hasHeader 是否包含標題行
     * @return K 線數據列表
     * @throws IOException 檔案讀取錯誤
     * @throws CsvFormatException 格式錯誤
     */
    public static List<Bar> importFromCsv(File file, boolean hasHeader) throws IOException, CsvFormatException {
        List<Bar> bars = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            boolean headerSkipped = false;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // 跳過空行和註解
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // 跳過標題行（第一個非註解非空行）
                if (hasHeader && !headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                
                try {
                    Bar bar = parseCsvLine(line, lineNumber);
                    bars.add(bar);
                } catch (Exception e) {
                    throw new CsvFormatException(
                        String.format("Line %d: %s - %s", lineNumber, line, e.getMessage()),
                        lineNumber
                    );
                }
            }
        }
        
        if (bars.isEmpty()) {
            throw new CsvFormatException("No valid data found in CSV file", 0);
        }
        
        // 按時間排序
        bars.sort(Comparator.comparing(Bar::getTimestamp));
        
        return bars;
    }
    
    /**
     * 解析 CSV 行
     * 支援格式：Timestamp,Open,High,Low,Close,Volume
     */
    private static Bar parseCsvLine(String line, int lineNumber) throws CsvFormatException {
        String[] parts = line.split(",");
        
        if (parts.length < 6) {
            throw new CsvFormatException(
                "Expected 6 columns (Timestamp,Open,High,Low,Close,Volume), found " + parts.length,
                lineNumber
            );
        }
        
        try {
            LocalDateTime timestamp = parseDateTime(parts[0].trim());
            double open = Double.parseDouble(parts[1].trim());
            double high = Double.parseDouble(parts[2].trim());
            double low = Double.parseDouble(parts[3].trim());
            double close = Double.parseDouble(parts[4].trim());
            long volume = Long.parseLong(parts[5].trim());
            
            // 驗證數據合理性
            validateBar(timestamp, open, high, low, close, volume);
            
            return new Bar(timestamp, open, high, low, close, volume);
            
        } catch (NumberFormatException e) {
            throw new CsvFormatException("Invalid number format: " + e.getMessage(), lineNumber);
        } catch (DateTimeParseException e) {
            throw new CsvFormatException("Invalid datetime format: " + e.getMessage(), lineNumber);
        }
    }
    
    /**
     * 解析日期時間（嘗試多種格式）
     */
    private static LocalDateTime parseDateTime(String dateTimeStr) {
        for (DateTimeFormatter formatter : SUPPORTED_FORMATS) {
            try {
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new DateTimeParseException("Unable to parse date/time: " + dateTimeStr, dateTimeStr, 0);
    }
    
    /**
     * 驗證 K 線數據
     */
    private static void validateBar(LocalDateTime timestamp, double open, double high, double low, double close, long volume) throws CsvFormatException {
        if (timestamp == null) {
            throw new CsvFormatException("Timestamp cannot be null", 0);
        }
        
        if (open <= 0 || high <= 0 || low <= 0 || close <= 0) {
            throw new CsvFormatException("Prices must be positive", 0);
        }
        
        if (high < low) {
            throw new CsvFormatException(
                String.format("High (%.2f) cannot be less than Low (%.2f)", high, low),
                0
            );
        }
        
        if (high < Math.max(open, close)) {
            throw new CsvFormatException(
                String.format("High (%.2f) must be >= max(Open, Close)", high),
                0
            );
        }
        
        if (low > Math.min(open, close)) {
            throw new CsvFormatException(
                String.format("Low (%.2f) must be <= min(Open, Close)", low),
                0
            );
        }
        
        if (volume < 0) {
            throw new CsvFormatException("Volume cannot be negative", 0);
        }
    }
    
    /**
     * 匯出 K 線數據到 CSV 檔案
     * 
     * @param bars K 線數據列表
     * @param file 輸出檔案
     * @param includeHeader 是否包含標題行
     * @throws IOException 檔案寫入錯誤
     */
    public static void exportToCsv(List<Bar> bars, File file, boolean includeHeader) throws IOException {
        exportToCsv(bars, file, includeHeader, "yyyy-MM-dd HH:mm:ss");
    }
    
    /**
     * 匯出 K 線數據到 CSV 檔案（支援自訂日期格式）
     * 
     * @param bars K 線數據列表
     * @param file 目標檔案
     * @param includeHeader 是否包含標題行
     * @param dateFormat 日期時間格式（例如："yyyy-MM-dd HH:mm:ss"）
     * @throws IOException 檔案寫入錯誤
     */
    public static void exportToCsv(List<Bar> bars, File file, boolean includeHeader, String dateFormat) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            
            // 寫入標題行
            if (includeHeader) {
                writer.println("Timestamp,Open,High,Low,Close,Volume");
            }
            
            // 寫入數據
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);
            for (Bar bar : bars) {
                writer.printf("%s,%.4f,%.4f,%.4f,%.4f,%d%n",
                    bar.getTimestamp().format(formatter),
                    bar.getOpen(),
                    bar.getHigh(),
                    bar.getLow(),
                    bar.getClose(),
                    bar.getVolume()
                );
            }
        }
    }
    
    /**
     * 驗證 CSV 檔案格式（不實際匯入）
     * 
     * @param file CSV 檔案
     * @param hasHeader 是否包含標題行
     * @return 驗證結果
     */
    public static CsvValidationResult validateCsvFile(File file, boolean hasHeader) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int validRows = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            boolean headerSkipped = false;
            
            LocalDateTime lastTimestamp = null;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // 跳過空行和註解
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // 跳過標題行（第一個非註解非空行）
                if (hasHeader && !headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                
                try {
                    Bar bar = parseCsvLine(line, lineNumber);
                    validRows++;
                    
                    // 檢查時間順序
                    if (lastTimestamp != null && bar.getTimestamp().isBefore(lastTimestamp)) {
                        warnings.add(String.format("Line %d: Timestamp is earlier than previous line", lineNumber));
                    }
                    lastTimestamp = bar.getTimestamp();
                    
                } catch (CsvFormatException e) {
                    errors.add(e.getMessage());
                }
            }
            
        } catch (IOException e) {
            errors.add("File read error: " + e.getMessage());
        }
        
        return new CsvValidationResult(validRows, errors, warnings);
    }
    
    /**
     * CSV 驗證結果
     */
    public static class CsvValidationResult {
        private final int validRows;
        private final List<String> errors;
        private final List<String> warnings;
        
        public CsvValidationResult(int validRows, List<String> errors, List<String> warnings) {
            this.validRows = validRows;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isValid() {
            return errors.isEmpty() && validRows > 0;
        }
        
        public int getValidRows() {
            return validRows;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Valid rows: %d%n", validRows));
            
            if (!errors.isEmpty()) {
                sb.append(String.format("Errors (%d):%n", errors.size()));
                for (String error : errors) {
                    sb.append("  - ").append(error).append("\n");
                }
            }
            
            if (!warnings.isEmpty()) {
                sb.append(String.format("Warnings (%d):%n", warnings.size()));
                for (String warning : warnings) {
                    sb.append("  - ").append(warning).append("\n");
                }
            }
            
            return sb.toString();
        }
    }
    
    /**
     * CSV 格式異常
     */
    public static class CsvFormatException extends Exception {
        private final int lineNumber;
        
        public CsvFormatException(String message, int lineNumber) {
            super(message);
            this.lineNumber = lineNumber;
        }
        
        public int getLineNumber() {
            return lineNumber;
        }
    }
}

