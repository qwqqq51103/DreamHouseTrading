package com.dreamhouse.trading.core.monitor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

/**
 * Shared entry cadence rules for auto-monitor and SQL radar replay.
 */
public final class AutoMonitorExecutionGate {

    public static final LocalTime DEFAULT_FORCE_CLOSE_TIME = LocalTime.of(13, 25);

    private AutoMonitorExecutionGate() {
    }

    public enum BlockType {
        TRADING_HALTED,
        POSITION_ALREADY_OPEN,
        FORCE_CLOSE_WINDOW,
        LATEST_ENTRY_TIME,
        EARLY_ENTRY_BLOCK,
        STOP_LOSS_COOLDOWN,
        DAILY_MAX_TRADES,
        ENTRY_PACING,
        ONE_ENTRY_PER_FIVE_MINUTE_BAR,
        MAX_OPEN_POSITIONS
    }

    public record GateDecision(boolean allowed, BlockType blockType, String reason) {
        public static GateDecision allow() {
            return new GateDecision(true, null, "");
        }

        public static GateDecision block(BlockType type, String reason) {
            return new GateDecision(false, type, reason != null ? reason : "");
        }
    }

    public record GateConfig(
            boolean earlyEntryBlockEnabled,
            LocalTime earlyEntryBlockStart,
            LocalTime earlyEntryBlockEnd,
            LocalTime latestAutoEntryTime,
            int dailyMaxAutoTrades,
            int entryPacingMinutes,
            boolean oneEntryPerFiveMinuteBar,
            LocalTime forceCloseTime) {

        public static GateConfig from(SignalMonitorConfig config) {
            SignalMonitorConfig effective = config != null ? config : SignalMonitorConfig.createDefault();
            return new GateConfig(
                    effective.isEarlyEntryBlockEnabled(),
                    effective.getEarlyEntryBlockStart(),
                    effective.getEarlyEntryBlockEnd(),
                    effective.getLatestAutoEntryTime(),
                    effective.getDailyMaxAutoTrades(),
                    effective.getEntryPacingMinutes(),
                    effective.isOneEntryPerFiveMinuteBar(),
                    DEFAULT_FORCE_CLOSE_TIME);
        }
    }

    public record GateState(
            String symbol,
            LocalDateTime signalTime,
            boolean tradingHalted,
            boolean positionAlreadyOpen,
            LocalDateTime cooldownUntil,
            int dailyEntryCount,
            LocalDateTime lastEntryTime,
            Set<String> entryBuckets,
            int openPositionCount,
            int maxOpenPositions) {
    }

    public static GateDecision evaluateOpenLong(GateConfig config, GateState state) {
        GateConfig effectiveConfig = config != null
                ? config
                : GateConfig.from(SignalMonitorConfig.createDefault());
        GateState effectiveState = state != null
                ? state
                : new GateState("", null, false, false, null, 0, null, Set.of(), 0, 1);
        LocalDateTime signalTime = effectiveState.signalTime() != null
                ? effectiveState.signalTime()
                : LocalDateTime.now();
        LocalTime time = signalTime.toLocalTime();

        if (effectiveState.tradingHalted()) {
            return GateDecision.block(BlockType.TRADING_HALTED, "trading halted by risk breaker");
        }
        if (effectiveState.positionAlreadyOpen()) {
            return GateDecision.block(BlockType.POSITION_ALREADY_OPEN, "position already open or pending");
        }
        LocalTime forceCloseTime = effectiveConfig.forceCloseTime() != null
                ? effectiveConfig.forceCloseTime()
                : DEFAULT_FORCE_CLOSE_TIME;
        if (!time.isBefore(forceCloseTime)) {
            return GateDecision.block(BlockType.FORCE_CLOSE_WINDOW, "force close window blocks new entries");
        }
        LocalTime latestEntryTime = effectiveConfig.latestAutoEntryTime();
        if (latestEntryTime != null && !time.isBefore(latestEntryTime)) {
            return GateDecision.block(BlockType.LATEST_ENTRY_TIME, "latest entry time reached");
        }
        if (isEarlyEntryBlocked(effectiveConfig, time)) {
            return GateDecision.block(BlockType.EARLY_ENTRY_BLOCK, "early session warmup blocks new entries");
        }
        LocalDateTime cooldownUntil = effectiveState.cooldownUntil();
        if (cooldownUntil != null && signalTime.isBefore(cooldownUntil)) {
            return GateDecision.block(BlockType.STOP_LOSS_COOLDOWN, "stop loss cooldown blocks new entries");
        }
        if (effectiveState.dailyEntryCount() >= Math.max(1, effectiveConfig.dailyMaxAutoTrades())) {
            return GateDecision.block(BlockType.DAILY_MAX_TRADES, "daily max auto trades reached");
        }
        if (effectiveConfig.entryPacingMinutes() > 0
                && effectiveState.lastEntryTime() != null
                && signalTime.isBefore(effectiveState.lastEntryTime().plusMinutes(effectiveConfig.entryPacingMinutes()))) {
            return GateDecision.block(BlockType.ENTRY_PACING, "entry pacing blocks new entries");
        }
        if (effectiveConfig.oneEntryPerFiveMinuteBar()
                && effectiveState.entryBuckets() != null
                && effectiveState.entryBuckets().contains(fiveMinuteBucket(signalTime))) {
            return GateDecision.block(BlockType.ONE_ENTRY_PER_FIVE_MINUTE_BAR, "one entry per M5 bar reached");
        }
        if (effectiveState.openPositionCount() >= Math.max(1, effectiveState.maxOpenPositions())) {
            return GateDecision.block(BlockType.MAX_OPEN_POSITIONS, "max open positions reached");
        }
        return GateDecision.allow();
    }

    public static String fiveMinuteBucket(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        int bucketMinute = (time.getMinute() / 5) * 5;
        return time.toLocalDate() + "T" + String.format("%02d:%02d", time.getHour(), bucketMinute);
    }

    public static boolean isSignalIntervalElapsed(LocalDateTime signalTime, LocalDateTime lastSignalTime, int minutes) {
        if (signalTime == null || lastSignalTime == null) {
            return true;
        }
        return Duration.between(lastSignalTime, signalTime).toMinutes() >= Math.max(0, minutes);
    }

    private static boolean isEarlyEntryBlocked(GateConfig config, LocalTime time) {
        if (!config.earlyEntryBlockEnabled() || time == null) {
            return false;
        }
        LocalTime start = config.earlyEntryBlockStart();
        LocalTime end = config.earlyEntryBlockEnd();
        if (start == null || end == null || !start.isBefore(end)) {
            return false;
        }
        return !time.isBefore(start) && time.isBefore(end);
    }
}
