package com.dreamhouse.trading.core.backtest;

import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.logging.ExitReason;
import com.dreamhouse.trading.core.logging.TradeRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps completed backtest trade pairs to semantic records for LogExporter.
 */
public final class BacktestTradeRecordMapper {

    private BacktestTradeRecordMapper() {
    }

    public static List<TradeRecord> toTradeRecords(
            BacktestResult result,
            String strategyName,
            String strategySettingSummary) {
        List<TradeRecord> records = new ArrayList<>();
        if (result == null) {
            return records;
        }

        List<Trade> trades = result.getTrades();
        int tradeNumber = 1;
        for (int i = 0; i < trades.size() - 1; i++) {
            Trade buy = trades.get(i);
            if (buy.getType() != TradeType.BUY) {
                continue;
            }
            Trade sell = trades.get(i + 1);
            if (sell.getType() != TradeType.SELL || !buy.getSymbol().equals(sell.getSymbol())) {
                continue;
            }

            records.add(toTradeRecord(
                    String.format(Locale.ROOT, "T%05d", tradeNumber++),
                    buy,
                    sell,
                    result,
                    strategyName,
                    strategySettingSummary));
            i++;
        }
        return records;
    }

    private static TradeRecord toTradeRecord(
            String tradeId,
            Trade buy,
            Trade sell,
            BacktestResult result,
            String strategyName,
            String strategySettingSummary) {
        double grossProfit = (sell.getPrice() - buy.getPrice()) * buy.getQuantity();
        double commission = buy.getCommissionAmount() + sell.getCommissionAmount();
        double tax = buy.getTaxAmount() + sell.getTaxAmount();
        double slippageCost = buy.getSlippageCost() + sell.getSlippageCost();
        double netProfit = sell.getNetProceeds() - buy.getTotalCost();
        double baseCost = buy.getTotalCost();
        double returnPercent = baseCost > 0.0 ? netProfit / baseCost : 0.0;

        return new TradeRecord.Builder(tradeId, buy.getSymbol())
                .tradeMode(result.getTradeMode() != null ? result.getTradeMode() : TradeMode.NO_TRADE)
                .timeframe(result.getTimeframe())
                .decisionSource(DecisionResult.DecisionSource.BACKTEST)
                .autoManaged(false)
                .entryTime(buy.getTimestamp())
                .entryPrice(buy.getPrice())
                .exitTime(sell.getTimestamp())
                .exitPrice(sell.getPrice())
                .quantity(buy.getQuantity())
                .entryCommission(buy.getCommission())
                .exitCommission(sell.getCommission())
                .stopLoss(buy.getStopLoss())
                .takeProfit(buy.getTakeProfit())
                .exitReason(resolveExitReason(sell.getExitReason()))
                .grossProfit(grossProfit)
                .commission(commission)
                .tax(tax)
                .slippageCost(slippageCost)
                .netProfit(netProfit)
                .returnPercent(returnPercent)
                .entryReason(buy.getExitReason())
                .exitReasonText(sell.getExitReason())
                .strategyName(strategyName)
                .strategySettingSummary(strategySettingSummary)
                .build();
    }

    static ExitReason resolveExitReason(String reason) {
        ExitReason exact = ExitReason.fromString(reason);
        if (exact != ExitReason.OTHER || reason == null || reason.isBlank()) {
            return exact;
        }

        String normalized = reason.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "EXIT_STOP_LOSS", "STOP_LOSS", "SL" -> ExitReason.STOP_LOSS;
            case "EXIT_TAKE_PROFIT", "TAKE_PROFIT", "TP" -> ExitReason.TAKE_PROFIT;
            case "EXIT_TRAILING_STOP", "TRAILING_STOP", "TSL" -> ExitReason.TRAILING_STOP;
            case "EXIT_FORCE_CLOSE", "FORCE_CLOSE", "FORCE_CLOSE_EOD", "EOD" -> ExitReason.FORCE_CLOSE_EOD;
            case "EXIT_VWAP_BREAK", "VWAP_BREAK", "STRATEGY_SIGNAL" -> ExitReason.STRATEGY_SIGNAL;
            case "SELL", "MANUAL_EXIT", "MANUAL" -> ExitReason.MANUAL_EXIT;
            default -> ExitReason.OTHER;
        };
    }
}
