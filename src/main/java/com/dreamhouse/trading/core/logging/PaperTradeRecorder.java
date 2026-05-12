package com.dreamhouse.trading.core.logging;

import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.execution.ExecutionResult;
import com.dreamhouse.trading.core.execution.OrderSide;
import com.dreamhouse.trading.core.scanner.MarketScanResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Appends paper-trading execution events and completed trade pairs for later strategy analysis.
 */
public class PaperTradeRecorder {

    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String ORDER_HEADER = String.join(",",
            "event_time",
            "source",
            "order_id",
            "position_id",
            "symbol",
            "side",
            "status",
            "order_status",
            "quantity",
            "requested_price",
            "executed_price",
            "stop_loss",
            "take_profit",
            "realized_pnl",
            "commission",
            "decision_action",
            "trade_mode",
            "confidence",
            "risk_reward",
            "reason",
            "message");

    private static final String TRADE_HEADER = String.join(",",
            "closed_time",
            "source",
            "position_id",
            "symbol",
            "trade_mode",
            "entry_time",
            "exit_time",
            "holding_minutes",
            "quantity",
            "entry_price",
            "exit_price",
            "stop_loss",
            "take_profit",
            "is_win",
            "outcome",
            "gross_pnl",
            "net_pnl",
            "return_pct",
            "mae",
            "mfe",
            "mae_pct",
            "mfe_pct",
            "setup_score",
            "setup_confidence",
            "setup_risk_reward",
            "entry_order_id",
            "exit_order_id",
            "entry_reason",
            "exit_reason",
            "raw_signal_summary",
            "block_reason");

    private static final String SETUP_HEADER = String.join(",",
            "entry_time",
            "source",
            "position_id",
            "order_id",
            "symbol",
            "trade_mode",
            "quantity",
            "entry_price",
            "stop_loss",
            "take_profit",
            "setup_score",
            "confidence",
            "risk_reward",
            "decision_action",
            "decision_source",
            "reason",
            "raw_signal_summary",
            "block_reason");

    private final Path outputDirectory;
    private final Map<String, OpenTrade> openTrades = new ConcurrentHashMap<>();

    public PaperTradeRecorder() {
        this(Paths.get("logs", "paper-trades"));
    }

    public PaperTradeRecorder(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void record(ExecutionResult result, DecisionResult decision, String source) {
        record(result, decision, null, source);
    }

    public void record(ExecutionResult result, DecisionResult decision, MarketScanResult scanResult, String source) {
        if (result == null) {
            return;
        }
        try {
            ensureDirectory();
            appendOrder(result, decision, source);
            updateCompletedTrade(result, decision, scanResult, source);
        } catch (IOException e) {
            System.err.println("[PaperTradeRecorder] failed to write trade log: " + e.getMessage());
        }
    }

    public void recordMarketPrice(String symbol, double price, LocalDateTime timestamp) {
        if (symbol == null || symbol.isBlank() || price <= 0.0) {
            return;
        }
        for (OpenTrade openTrade : openTrades.values()) {
            if (symbol.equals(openTrade.symbol())) {
                openTrade.observePrice(price, timestamp);
            }
        }
    }

    public Path getTodayOrderLogPath() {
        return outputDirectory.resolve("orders_" + LocalDate.now().format(DATE_FORMAT) + ".csv");
    }

    public Path getTodayCompletedTradeLogPath() {
        return outputDirectory.resolve("completed_trades_" + LocalDate.now().format(DATE_FORMAT) + ".csv");
    }

    public Path getTodaySetupLogPath() {
        return outputDirectory.resolve("trade_setups_" + LocalDate.now().format(DATE_FORMAT) + ".csv");
    }

    private void appendOrder(ExecutionResult result, DecisionResult decision, String source) throws IOException {
        appendCsvLine(getTodayOrderLogPath(), ORDER_HEADER, String.join(",",
                csv(formatTime(result.getExecutionTime())),
                csv(source),
                csv(result.getOrderId()),
                csv(result.getPositionId()),
                csv(result.getSymbol()),
                csv(result.getOrderSide() != null ? result.getOrderSide().name() : ""),
                csv(result.getStatus() != null ? result.getStatus().name() : ""),
                csv(result.getOrderStatus() != null ? result.getOrderStatus().name() : ""),
                number(result.getExecutedQuantity()),
                decimal(result.getRequestedPrice()),
                decimal(result.getExecutedPrice()),
                optionalDecimal(result.getStopLoss()),
                optionalDecimal(result.getTakeProfit()),
                decimal(result.getRealizedPnL()),
                decimal(result.getCommission()),
                csv(decision != null && decision.getAction() != null ? decision.getAction().name() : ""),
                csv(resolveTradeMode(decision).name()),
                decimal(decision != null ? decision.getConfidence() : 0.0),
                optionalDecimal(decision != null ? decision.getRiskRewardRatio() : null),
                csv(result.getDecisionReason()),
                csv(result.getMessage())));
    }

    private void updateCompletedTrade(ExecutionResult result, DecisionResult decision, MarketScanResult scanResult, String source) throws IOException {
        if (!result.isSuccess() || result.getOrderSide() == null) {
            return;
        }
        String positionId = result.getPositionId();
        if (positionId == null || positionId.isBlank()) {
            positionId = result.getOrderId();
        }

        if (result.getOrderSide().opensExposure()) {
            OpenTrade openTrade = OpenTrade.from(result, decision, scanResult);
            openTrades.put(positionId, openTrade);
            appendSetup(result, decision, scanResult, source);
            return;
        }

        if (result.getOrderSide() != OrderSide.SELL) {
            return;
        }

        OpenTrade open = openTrades.remove(positionId);
        if (open == null) {
            open = OpenTrade.fromUnknownEntry(result, decision);
        }

        double grossPnL = (result.getExecutedPrice() - open.entryPrice()) * result.getExecutedQuantity();
        double netPnL = result.getRealizedPnL();
        double returnPct = open.entryPrice() > 0.0
                ? (result.getExecutedPrice() - open.entryPrice()) / open.entryPrice() * 100.0
                : 0.0;
        long holdingMinutes = open.entryTime() != null && result.getExecutionTime() != null
                ? Math.max(0L, Duration.between(open.entryTime(), result.getExecutionTime()).toMinutes())
                : 0L;

        appendCsvLine(getTodayCompletedTradeLogPath(), TRADE_HEADER, String.join(",",
                csv(formatTime(LocalDateTime.now())),
                csv(source),
                csv(positionId),
                csv(result.getSymbol()),
                csv(open.tradeMode().name()),
                csv(formatTime(open.entryTime())),
                csv(formatTime(result.getExecutionTime())),
                number(holdingMinutes),
                number(result.getExecutedQuantity()),
                decimal(open.entryPrice()),
                decimal(result.getExecutedPrice()),
                optionalDecimal(open.stopLoss()),
                optionalDecimal(open.takeProfit()),
                csv(netPnL > 0.0 ? "true" : "false"),
                csv(netPnL > 0.0 ? "WIN" : netPnL < 0.0 ? "LOSS" : "BREAKEVEN"),
                decimal(grossPnL),
                decimal(netPnL),
                decimal(returnPct),
                decimal(open.maeAmount(result.getExecutedQuantity())),
                decimal(open.mfeAmount(result.getExecutedQuantity())),
                decimal(open.maePercent()),
                decimal(open.mfePercent()),
                decimal(open.setupScore()),
                decimal(open.setupConfidence()),
                optionalDecimal(open.setupRiskReward()),
                csv(open.orderId()),
                csv(result.getOrderId()),
                csv(open.reason()),
                csv(result.getDecisionReason()),
                csv(open.rawSignalSummary()),
                csv(open.blockReason())));
    }

    private void appendSetup(ExecutionResult result, DecisionResult decision, MarketScanResult scanResult, String source) throws IOException {
        appendCsvLine(getTodaySetupLogPath(), SETUP_HEADER, String.join(",",
                csv(formatTime(result.getExecutionTime())),
                csv(source),
                csv(result.getPositionId()),
                csv(result.getOrderId()),
                csv(result.getSymbol()),
                csv(resolveTradeMode(decision, scanResult).name()),
                number(result.getExecutedQuantity()),
                decimal(result.getExecutedPrice()),
                optionalDecimal(result.getStopLoss()),
                optionalDecimal(result.getTakeProfit()),
                decimal(scanResult != null ? scanResult.getScore() : 0.0),
                decimal(resolveConfidence(decision, scanResult)),
                optionalDecimal(resolveRiskReward(decision, scanResult)),
                csv(decision != null && decision.getAction() != null ? decision.getAction().name() : ""),
                csv(decision != null && decision.getSource() != null ? decision.getSource().name() : ""),
                csv(result.getDecisionReason()),
                csv(scanResult != null ? scanResult.getRawSignalSummary() : ""),
                csv(scanResult != null ? scanResult.getBlockReason() : "")));
    }

    private void appendCsvLine(Path path, String header, String line) throws IOException {
        boolean newFile = Files.notExists(path) || Files.size(path) == 0L;
        if (!newFile) {
            ensureUtf8Bom(path);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            if (newFile) {
                writer.write('\ufeff');
                writer.write(header);
                writer.newLine();
            }
            writer.write(line);
            writer.newLine();
        }
    }

    private void ensureUtf8Bom(Path path) throws IOException {
        if (Files.notExists(path) || Files.size(path) == 0L || hasUtf8Bom(path)) {
            return;
        }
        byte[] content = Files.readAllBytes(path);
        byte[] withBom = new byte[content.length + UTF8_BOM.length];
        System.arraycopy(UTF8_BOM, 0, withBom, 0, UTF8_BOM.length);
        System.arraycopy(content, 0, withBom, UTF8_BOM.length, content.length);
        Files.write(path, withBom, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private boolean hasUtf8Bom(Path path) throws IOException {
        if (Files.size(path) < UTF8_BOM.length) {
            return false;
        }
        byte[] firstBytes = new byte[UTF8_BOM.length];
        try (var input = Files.newInputStream(path)) {
            int read = input.read(firstBytes);
            return read == UTF8_BOM.length
                    && firstBytes[0] == UTF8_BOM[0]
                    && firstBytes[1] == UTF8_BOM[1]
                    && firstBytes[2] == UTF8_BOM[2];
        }
    }

    private void ensureDirectory() throws IOException {
        Files.createDirectories(outputDirectory);
    }

    private static TradeMode resolveTradeMode(DecisionResult decision) {
        return resolveTradeMode(decision, null);
    }

    private static TradeMode resolveTradeMode(DecisionResult decision, MarketScanResult scanResult) {
        if (decision != null && decision.getTradeMode() != null) {
            return decision.getTradeMode();
        }
        return scanResult != null && scanResult.getTradeMode() != null ? scanResult.getTradeMode() : TradeMode.NO_TRADE;
    }

    private static double resolveConfidence(DecisionResult decision, MarketScanResult scanResult) {
        if (decision != null) {
            return decision.getConfidence();
        }
        return scanResult != null ? scanResult.getConfidence() : 0.0;
    }

    private static Double resolveRiskReward(DecisionResult decision, MarketScanResult scanResult) {
        if (decision != null && decision.getRiskRewardRatio() != null) {
            return decision.getRiskRewardRatio();
        }
        return scanResult != null ? scanResult.getRiskRewardRatio() : null;
    }

    private static String formatTime(LocalDateTime time) {
        return time != null ? time.format(TIME_FORMAT) : "";
    }

    private static String csv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static String number(long value) {
        return Long.toString(value);
    }

    private static String decimal(double value) {
        return String.format(java.util.Locale.US, "%.6f", value);
    }

    private static String optionalDecimal(Double value) {
        return value != null ? decimal(value) : "";
    }

    private static class OpenTrade {
        private final String symbol;
        private final String orderId;
        private final LocalDateTime entryTime;
        private final double entryPrice;
        private final Double stopLoss;
        private final Double takeProfit;
        private final TradeMode tradeMode;
        private final String reason;
        private final double setupScore;
        private final double setupConfidence;
        private final Double setupRiskReward;
        private final String rawSignalSummary;
        private final String blockReason;
        private double highestPrice;
        private double lowestPrice;
        private LocalDateTime highestTime;
        private LocalDateTime lowestTime;

        private OpenTrade(
                String symbol,
                String orderId,
                LocalDateTime entryTime,
                double entryPrice,
                Double stopLoss,
                Double takeProfit,
                TradeMode tradeMode,
                String reason,
                double setupScore,
                double setupConfidence,
                Double setupRiskReward,
                String rawSignalSummary,
                String blockReason) {
            this.symbol = symbol;
            this.orderId = orderId;
            this.entryTime = entryTime;
            this.entryPrice = entryPrice;
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
            this.tradeMode = tradeMode;
            this.reason = reason;
            this.setupScore = setupScore;
            this.setupConfidence = setupConfidence;
            this.setupRiskReward = setupRiskReward;
            this.rawSignalSummary = rawSignalSummary;
            this.blockReason = blockReason;
            this.highestPrice = entryPrice;
            this.lowestPrice = entryPrice;
            this.highestTime = entryTime;
            this.lowestTime = entryTime;
        }

        static OpenTrade from(ExecutionResult result, DecisionResult decision, MarketScanResult scanResult) {
            return new OpenTrade(
                    result.getSymbol(),
                    result.getOrderId(),
                    result.getExecutionTime(),
                    result.getExecutedPrice(),
                    result.getStopLoss(),
                    result.getTakeProfit(),
                    resolveTradeMode(decision, scanResult),
                    result.getDecisionReason(),
                    scanResult != null ? scanResult.getScore() : 0.0,
                    resolveConfidence(decision, scanResult),
                    resolveRiskReward(decision, scanResult),
                    scanResult != null ? scanResult.getRawSignalSummary() : "",
                    scanResult != null ? scanResult.getBlockReason() : "");
        }

        static OpenTrade fromUnknownEntry(ExecutionResult result, DecisionResult decision) {
            return new OpenTrade(
                    result.getSymbol(),
                    "",
                    null,
                    result.getRequestedPrice(),
                    result.getStopLoss(),
                    result.getTakeProfit(),
                    resolveTradeMode(decision),
                    "",
                    0.0,
                    resolveConfidence(decision, null),
                    resolveRiskReward(decision, null),
                    "",
                    "");
        }

        void observePrice(double price, LocalDateTime timestamp) {
            if (entryPrice <= 0.0 || price <= 0.0) {
                return;
            }
            if (price > highestPrice) {
                highestPrice = price;
                highestTime = timestamp;
            }
            if (price < lowestPrice) {
                lowestPrice = price;
                lowestTime = timestamp;
            }
        }

        String symbol() {
            return symbol;
        }

        String orderId() {
            return orderId;
        }

        LocalDateTime entryTime() {
            return entryTime;
        }

        double entryPrice() {
            return entryPrice;
        }

        Double stopLoss() {
            return stopLoss;
        }

        Double takeProfit() {
            return takeProfit;
        }

        TradeMode tradeMode() {
            return tradeMode;
        }

        String reason() {
            return reason;
        }

        double setupScore() {
            return setupScore;
        }

        double setupConfidence() {
            return setupConfidence;
        }

        Double setupRiskReward() {
            return setupRiskReward;
        }

        String rawSignalSummary() {
            return rawSignalSummary;
        }

        String blockReason() {
            return blockReason;
        }

        double maeAmount(int quantity) {
            return Math.min(0.0, (lowestPrice - entryPrice) * quantity);
        }

        double mfeAmount(int quantity) {
            return Math.max(0.0, (highestPrice - entryPrice) * quantity);
        }

        double maePercent() {
            return entryPrice > 0.0 ? (lowestPrice - entryPrice) / entryPrice * 100.0 : 0.0;
        }

        double mfePercent() {
            return entryPrice > 0.0 ? (highestPrice - entryPrice) / entryPrice * 100.0 : 0.0;
        }
    }
}
