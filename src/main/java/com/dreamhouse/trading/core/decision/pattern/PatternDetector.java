package com.dreamhouse.trading.core.decision.pattern;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;

/**
 * 型態檢測器
 *
 * 基礎版本：檢測常見的 K 線型態
 *
 * POC 階段實作：
 * - 錘子線 (HAMMER)
 * - 吊人線 (HANGING_MAN)
 * - 吞沒型態 (ENGULFING)
 * - 十字星 (DOJI)
 */
public class PatternDetector {

    private final BarSeries barSeries;
    private final String symbol;

    // 型態檢測參數
    private double dojiBodyRatio = 0.1;           // 十字星實體比例
    private double hammerShadowRatio = 2.0;        // 錘子線影線比例
    private double engulfingMinRatio = 1.0;        // 吞沒型態最小比例

    public PatternDetector(BarSeries barSeries, String symbol) {
        this.barSeries = barSeries;
        this.symbol = symbol;
    }

    /**
     * 檢測當前 K 線的型態
     *
     * @param barIndex 當前 K 線索引
     * @return 型態上下文
     */
    public PatternContext detect(int barIndex) {
        if (barIndex < 2) {
            // 數據不足，無法檢測
            return new PatternContext.Builder()
                    .symbol(symbol)
                    .primaryPattern(PatternType.NONE)
                    .suggestion("數據不足")
                    .build();
        }

        List<DetectedPattern> patterns = new ArrayList<>();

        // 檢測單 K 型態
        DetectedPattern doji = detectDoji(barIndex);
        if (doji != null) patterns.add(doji);

        DetectedPattern hammer = detectHammer(barIndex);
        if (hammer != null) patterns.add(hammer);

        DetectedPattern hangingMan = detectHangingMan(barIndex);
        if (hangingMan != null) patterns.add(hangingMan);

        // 檢測雙 K 型態
        DetectedPattern engulfing = detectEngulfing(barIndex);
        if (engulfing != null) patterns.add(engulfing);

        // 選擇主要型態（優先級：吞沒 > 錘子/吊人 > 十字星）
        PatternType primaryPattern = PatternType.NONE;
        double maxConfidence = 0.0;
        DetectedPattern primaryDetection = null;

        for (DetectedPattern pattern : patterns) {
            if (pattern.getConfidence() > maxConfidence) {
                maxConfidence = pattern.getConfidence();
                primaryPattern = pattern.getPatternType();
                primaryDetection = pattern;
            }
        }

        // 建立型態上下文
        PatternContext.Builder builder = new PatternContext.Builder()
                .symbol(symbol)
                .primaryPattern(primaryPattern)
                .patternConfidence(maxConfidence);

        // 添加所有檢測到的型態
        for (DetectedPattern pattern : patterns) {
            builder.addPattern(pattern);
        }

        // 根據型態設定建議和風險倍數
        if (primaryDetection != null) {
            builder.suggestion(generateSuggestion(primaryPattern, maxConfidence));
            builder.riskMultiplier(calculateRiskMultiplier(primaryPattern, maxConfidence));
        }

        return builder.build();
    }

    /**
     * 檢測十字星型態
     */
    private DetectedPattern detectDoji(int barIndex) {
        Bar bar = barSeries.getBar(barIndex);

        double open = bar.getOpenPrice().doubleValue();
        double close = bar.getClosePrice().doubleValue();
        double high = bar.getHighPrice().doubleValue();
        double low = bar.getLowPrice().doubleValue();

        double bodySize = Math.abs(close - open);
        double totalRange = high - low;

        if (totalRange == 0) return null;

        double bodyRatio = bodySize / totalRange;

        if (bodyRatio <= dojiBodyRatio) {
            // 十字星：實體很小
            double confidence = 0.6 + (dojiBodyRatio - bodyRatio) * 2.0;
            confidence = Math.min(0.9, confidence);

            return new DetectedPattern.Builder(PatternType.DOJI)
                    .confidence(confidence)
                    .startBarIndex(barIndex)
                    .endBarIndex(barIndex)
                    .description("十字星型態，市場猶豫不決")
                    .build();
        }

        return null;
    }

    /**
     * 檢測錘子線（看漲反轉）
     */
    private DetectedPattern detectHammer(int barIndex) {
        Bar bar = barSeries.getBar(barIndex);

        double open = bar.getOpenPrice().doubleValue();
        double close = bar.getClosePrice().doubleValue();
        double high = bar.getHighPrice().doubleValue();
        double low = bar.getLowPrice().doubleValue();

        double bodySize = Math.abs(close - open);
        double lowerShadow = Math.min(open, close) - low;
        double upperShadow = high - Math.max(open, close);

        if (bodySize == 0) return null;

        // 錘子線特徵：下影線很長，上影線很短，實體在上方
        if (lowerShadow >= bodySize * hammerShadowRatio && upperShadow < bodySize * 0.5) {
            // 需要在下跌趨勢中出現才算有效
            if (barIndex >= 3 && isDowntrend(barIndex - 3, barIndex - 1)) {
                double confidence = 0.65 + Math.min(0.25, lowerShadow / bodySize * 0.05);

                return new DetectedPattern.Builder(PatternType.HAMMER)
                        .confidence(confidence)
                        .startBarIndex(barIndex)
                        .endBarIndex(barIndex)
                        .description("錘子線，可能反轉向上")
                        .build();
            }
        }

        return null;
    }

    /**
     * 檢測吊人線（看跌反轉）
     */
    private DetectedPattern detectHangingMan(int barIndex) {
        Bar bar = barSeries.getBar(barIndex);

        double open = bar.getOpenPrice().doubleValue();
        double close = bar.getClosePrice().doubleValue();
        double high = bar.getHighPrice().doubleValue();
        double low = bar.getLowPrice().doubleValue();

        double bodySize = Math.abs(close - open);
        double lowerShadow = Math.min(open, close) - low;
        double upperShadow = high - Math.max(open, close);

        if (bodySize == 0) return null;

        // 吊人線特徵：形狀同錘子線，但出現在上升趨勢
        if (lowerShadow >= bodySize * hammerShadowRatio && upperShadow < bodySize * 0.5) {
            // 需要在上升趨勢中出現才算有效
            if (barIndex >= 3 && isUptrend(barIndex - 3, barIndex - 1)) {
                double confidence = 0.65 + Math.min(0.25, lowerShadow / bodySize * 0.05);

                return new DetectedPattern.Builder(PatternType.HANGING_MAN)
                        .confidence(confidence)
                        .startBarIndex(barIndex)
                        .endBarIndex(barIndex)
                        .description("吊人線，可能反轉向下")
                        .build();
            }
        }

        return null;
    }

    /**
     * 檢測吞沒型態
     */
    private DetectedPattern detectEngulfing(int barIndex) {
        if (barIndex < 1) return null;

        Bar prevBar = barSeries.getBar(barIndex - 1);
        Bar currBar = barSeries.getBar(barIndex);

        double prevOpen = prevBar.getOpenPrice().doubleValue();
        double prevClose = prevBar.getClosePrice().doubleValue();
        double currOpen = currBar.getOpenPrice().doubleValue();
        double currClose = currBar.getClosePrice().doubleValue();

        double prevBody = Math.abs(prevClose - prevOpen);
        double currBody = Math.abs(currClose - currOpen);

        if (prevBody == 0 || currBody == 0) return null;

        // 看漲吞沒：前陰後陽，且陽線吞沒陰線
        if (prevClose < prevOpen && currClose > currOpen) {
            if (currOpen <= prevClose && currClose >= prevOpen) {
                double ratio = currBody / prevBody;
                if (ratio >= engulfingMinRatio) {
                    double confidence = 0.7 + Math.min(0.2, (ratio - 1.0) * 0.2);

                    return new DetectedPattern.Builder(PatternType.BULLISH_ENGULFING)
                            .confidence(confidence)
                            .startBarIndex(barIndex - 1)
                            .endBarIndex(barIndex)
                            .description("看漲吞沒型態")
                            .build();
                }
            }
        }

        // 看跌吞沒：前陽後陰，且陰線吞沒陽線
        if (prevClose > prevOpen && currClose < currOpen) {
            if (currOpen >= prevClose && currClose <= prevOpen) {
                double ratio = currBody / prevBody;
                if (ratio >= engulfingMinRatio) {
                    double confidence = 0.7 + Math.min(0.2, (ratio - 1.0) * 0.2);

                    return new DetectedPattern.Builder(PatternType.BEARISH_ENGULFING)
                            .confidence(confidence)
                            .startBarIndex(barIndex - 1)
                            .endBarIndex(barIndex)
                            .description("看跌吞沒型態")
                            .build();
                }
            }
        }

        return null;
    }

    /**
     * 判斷是否為上升趨勢
     */
    private boolean isUptrend(int startIndex, int endIndex) {
        if (startIndex >= endIndex) return false;

        double startPrice = barSeries.getBar(startIndex).getClosePrice().doubleValue();
        double endPrice = barSeries.getBar(endIndex).getClosePrice().doubleValue();

        return endPrice > startPrice * 1.01;  // 至少上漲 1%
    }

    /**
     * 判斷是否為下降趨勢
     */
    private boolean isDowntrend(int startIndex, int endIndex) {
        if (startIndex >= endIndex) return false;

        double startPrice = barSeries.getBar(startIndex).getClosePrice().doubleValue();
        double endPrice = barSeries.getBar(endIndex).getClosePrice().doubleValue();

        return endPrice < startPrice * 0.99;  // 至少下跌 1%
    }

    /**
     * 生成型態建議
     */
    private String generateSuggestion(PatternType pattern, double confidence) {
        if (pattern.isBullish()) {
            return String.format("看漲型態，建議關注做多機會 (信心度:%.0f%%)", confidence * 100);
        } else if (pattern.isBearish()) {
            return String.format("看跌型態，建議關注做空機會 (信心度:%.0f%%)", confidence * 100);
        } else if (pattern == PatternType.DOJI) {
            return "市場猶豫，等待方向明確";
        } else {
            return "未識別明確型態";
        }
    }

    /**
     * 計算風險倍數
     *
     * 高信心度型態可以增加風險，低信心度降低風險
     */
    private double calculateRiskMultiplier(PatternType pattern, double confidence) {
        if (pattern == PatternType.NONE) {
            return 1.0;
        }

        // 基礎倍數根據信心度
        double baseMultiplier = 0.8 + (confidence * 0.4);  // 0.8 ~ 1.2

        // 反轉型態更謹慎
        if (pattern.isReversal()) {
            baseMultiplier *= 0.9;
        }

        return Math.max(0.5, Math.min(2.0, baseMultiplier));
    }

    // 配置方法

    public void setDojiBodyRatio(double ratio) {
        this.dojiBodyRatio = ratio;
    }

    public void setHammerShadowRatio(double ratio) {
        this.hammerShadowRatio = ratio;
    }

    public void setEngulfingMinRatio(double ratio) {
        this.engulfingMinRatio = ratio;
    }
}
