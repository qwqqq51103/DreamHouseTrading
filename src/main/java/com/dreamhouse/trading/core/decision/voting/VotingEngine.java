package com.dreamhouse.trading.core.decision.voting;

import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import com.dreamhouse.trading.core.decision.signal.SignalType;
import com.dreamhouse.trading.core.decision.regime.AllowedSide;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 多策略投票引擎
 * 負責整合多個策略的信號，並根據加權投票產生最終決策
 */
public class VotingEngine {

    private final VotingConfig config;
    private boolean hasPosition;  // 當前是否持有部位
    private final List<IStrategySignal> recentSignals;  // 最近的信號（用於除錯）

    public VotingEngine(VotingConfig config) {
        this.config = config;
        this.hasPosition = false;
        this.recentSignals = new ArrayList<>();
    }

    /**
     * 設定當前持倉狀態
     */
    public void setHasPosition(boolean hasPosition) {
        this.hasPosition = hasPosition;
    }

    /**
     * 執行投票決策（無持倉時）
     *
     * @param signals      所有策略的信號列表
     * @param allowedSide  允許的交易方向（由 MarketRegimeDetector 提供）
     * @return 投票結果
     */
    public VotingResult voteForEntry(List<IStrategySignal> signals, AllowedSide allowedSide) {
        if (hasPosition) {
            return new VotingResult.Builder()
                    .decision(VotingResult.Decision.NO_ACTION)
                    .reason("已有持倉，不進行進場投票")
                    .build();
        }

        // 過濾過期信號
        List<IStrategySignal> validSignals = filterValidSignals(signals);

        // 檢查最小投票策略數量
        if (validSignals.size() < config.getMinVotingStrategies()) {
            return new VotingResult.Builder()
                    .decision(VotingResult.Decision.NO_ACTION)
                    .reason(String.format("有效信號數(%d) < 最小要求(%d)",
                            validSignals.size(), config.getMinVotingStrategies()))
                    .signals(validSignals)
                    .build();
        }

        // 計算各方向的加權分數
        double longScore = calculateScore(validSignals, SignalType.LONG);
        double shortScore = calculateScore(validSignals, SignalType.SHORT);

        VotingResult.Builder resultBuilder = new VotingResult.Builder()
                .longScore(longScore)
                .shortScore(shortScore)
                .votingStrategyCount(validSignals.size())
                .signals(validSignals);

        // 根據允許方向和分數決定進場
        if (allowedSide.allowsLong() && longScore >= config.getLongEntryThreshold()) {
            return resultBuilder
                    .decision(VotingResult.Decision.ENTER_LONG)
                    .reason(String.format("多頭分數%.3f >= 閾值%.3f", longScore, config.getLongEntryThreshold()))
                    .build();
        }

        if (allowedSide.allowsShort() && shortScore >= config.getShortEntryThreshold()) {
            return resultBuilder
                    .decision(VotingResult.Decision.ENTER_SHORT)
                    .reason(String.format("空頭分數%.3f >= 閾值%.3f", shortScore, config.getShortEntryThreshold()))
                    .build();
        }

        // 分數未達標或方向不被允許
        String reason;
        if (!allowedSide.allowsAnyTrade()) {
            reason = "當前市場環境不允許交易";
        } else if (allowedSide == AllowedSide.LONG_ONLY) {
            reason = String.format("僅允許做多，但多頭分數%.3f < 閾值%.3f", longScore, config.getLongEntryThreshold());
        } else if (allowedSide == AllowedSide.SHORT_ONLY) {
            reason = String.format("僅允許做空，但空頭分數%.3f < 閾值%.3f", shortScore, config.getShortEntryThreshold());
        } else {
            reason = String.format("多頭分數%.3f和空頭分數%.3f均未達閾值", longScore, shortScore);
        }

        return resultBuilder
                .decision(VotingResult.Decision.NO_ACTION)
                .reason(reason)
                .build();
    }

    /**
     * 執行投票決策（有持倉時）
     *
     * @param signals 所有策略的信號列表
     * @return 投票結果
     */
    public VotingResult voteForExit(List<IStrategySignal> signals) {
        if (!hasPosition) {
            return new VotingResult.Builder()
                    .decision(VotingResult.Decision.NO_ACTION)
                    .reason("無持倉，不進行出場投票")
                    .build();
        }

        // 過濾過期信號
        List<IStrategySignal> validSignals = filterValidSignals(signals);

        if (validSignals.isEmpty()) {
            return new VotingResult.Builder()
                    .decision(VotingResult.Decision.HOLD)
                    .reason("無有效信號，繼續持有")
                    .build();
        }

        // 計算出場和反手分數
        double exitScore = calculateScore(validSignals, SignalType.EXIT);
        double reverseScore = calculateScore(validSignals, SignalType.REVERSE);

        VotingResult.Builder resultBuilder = new VotingResult.Builder()
                .exitScore(exitScore)
                .reverseScore(reverseScore)
                .votingStrategyCount(validSignals.size())
                .signals(validSignals);

        // 檢查反手信號（如果啟用）
        if (config.isReverseEnabled() && reverseScore >= config.getReverseThreshold()) {
            // 判斷反手方向（根據新的 LONG/SHORT 信號）
            double longScore = calculateScore(validSignals, SignalType.LONG);
            double shortScore = calculateScore(validSignals, SignalType.SHORT);

            if (longScore > shortScore) {
                return resultBuilder
                        .longScore(longScore)
                        .decision(VotingResult.Decision.REVERSE_LONG)
                        .reason(String.format("反手分數%.3f >= 閾值%.3f，反手做多", reverseScore, config.getReverseThreshold()))
                        .build();
            } else {
                return resultBuilder
                        .shortScore(shortScore)
                        .decision(VotingResult.Decision.REVERSE_SHORT)
                        .reason(String.format("反手分數%.3f >= 閾值%.3f，反手做空", reverseScore, config.getReverseThreshold()))
                        .build();
            }
        }

        // 檢查一般出場信號
        if (exitScore >= config.getExitThreshold()) {
            return resultBuilder
                    .decision(VotingResult.Decision.EXIT)
                    .reason(String.format("出場分數%.3f >= 閾值%.3f", exitScore, config.getExitThreshold()))
                    .build();
        }

        // 繼續持有
        return resultBuilder
                .decision(VotingResult.Decision.HOLD)
                .reason(String.format("出場分數%.3f < 閾值%.3f，繼續持有", exitScore, config.getExitThreshold()))
                .build();
    }

    /**
     * 過濾有效信號（移除過期信號）
     */
    private List<IStrategySignal> filterValidSignals(List<IStrategySignal> signals) {
        long currentTime = System.currentTimeMillis();
        long validityMs = config.getSignalValidityMs();

        return signals.stream()
                .filter(signal -> (currentTime - signal.getTimestamp()) <= validityMs)
                .filter(signal -> signal.getSignal() != SignalType.NO_TRADE)
                .collect(Collectors.toList());
    }

    /**
     * 計算特定信號類型的加權分數
     */
    private double calculateScore(List<IStrategySignal> signals, SignalType targetType) {
        double totalScore = 0.0;

        for (IStrategySignal signal : signals) {
            if (signal.getSignal() == targetType) {
                double weightedScore = signal.getConfidence() * signal.getWeight();
                totalScore += weightedScore;
            }
        }

        return totalScore;
    }

    /**
     * 檢查策略一致性（所有策略是否方向一致）
     */
    private boolean checkConsensus(List<IStrategySignal> signals) {
        if (signals.isEmpty()) {
            return false;
        }

        SignalType firstSignal = signals.get(0).getSignal();
        return signals.stream()
                .allMatch(s -> s.getSignal() == firstSignal);
    }

    /**
     * 獲取最近的信號（用於除錯）
     */
    public List<IStrategySignal> getRecentSignals() {
        return new ArrayList<>(recentSignals);
    }

    /**
     * 清空歷史信號
     */
    public void clearRecentSignals() {
        recentSignals.clear();
    }

    public VotingConfig getConfig() {
        return config;
    }
}
