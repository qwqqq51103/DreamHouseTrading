package com.dreamhouse.trading.core;

import org.ta4j.core.*;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.bollinger.*;
import org.ta4j.core.indicators.statistics.*;
import org.ta4j.core.indicators.volume.*;
import org.ta4j.core.indicators.adx.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

public class IndicatorService {
    private BarSeries barSeries;
    private final int maxBars;
    
    public IndicatorService(String name, int maxBars) {
        this.maxBars = maxBars;
        this.barSeries = new BaseBarSeries(name);
        this.barSeries.setMaximumBarCount(maxBars);
    }
    
    public void addBar(ZonedDateTime endTime, double open, double high, double low, double close, double volume) {
        Bar bar = new BaseBar(
            Duration.ofMinutes(1),
            endTime,
            BigDecimal.valueOf(open),
            BigDecimal.valueOf(high),
            BigDecimal.valueOf(low),
            BigDecimal.valueOf(close),
            BigDecimal.valueOf(volume)
        );
        barSeries.addBar(bar);
    }
    
    public BarSeries getBarSeries() {
        return barSeries;
    }
    
    public void resetSeries(String name) {
        barSeries = new BaseBarSeries(name);
        barSeries.setMaximumBarCount(maxBars);
    }
    
    // 取得 SMA 指標
    public List<Double> getSMA(int period) {
        if (barSeries.getBarCount() < period) {
            return Collections.emptyList();
        }
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            if (!sma.getValue(i).isNaN()) {
                result.add(sma.getValue(i).doubleValue());
            } else {
                result.add(null);
            }
        }
        return result;
    }
    
    // 取得 EMA 指標
    public List<Double> getEMA(int period) {
        if (barSeries.getBarCount() < period) {
            return Collections.emptyList();
        }
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        EMAIndicator ema = new EMAIndicator(closePrice, period);
        
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            if (!ema.getValue(i).isNaN()) {
                result.add(ema.getValue(i).doubleValue());
            } else {
                result.add(null);
            }
        }
        return result;
    }
    
    // 取得 RSI 指標
    public List<Double> getRSI(int period) {
        //System.out.println("計算 RSI，週期: " + period + "，數據量: " + barSeries.getBarCount());
        if (barSeries.getBarCount() < period + 1) {
            //System.out.println("RSI 數據不足，需要 " + (period + 1) + " 根K線");
            return Collections.emptyList();
        }
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        RSIIndicator rsi = new RSIIndicator(closePrice, period);
        
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            if (!rsi.getValue(i).isNaN()) {
                result.add(rsi.getValue(i).doubleValue());
            } else {
                result.add(null);
            }
        }
        return result;
    }
    
    // 取得 MACD 指標（返回 [MACD線, Signal線, Histogram]）
    public Map<String, List<Double>> getMACD(int fastPeriod, int slowPeriod, int signalPeriod) {
        Map<String, List<Double>> result = new HashMap<>();
        
        if (barSeries.getBarCount() < slowPeriod + signalPeriod) {
            result.put("macd", Collections.emptyList());
            result.put("signal", Collections.emptyList());
            result.put("histogram", Collections.emptyList());
            return result;
        }
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        MACDIndicator macd = new MACDIndicator(closePrice, fastPeriod, slowPeriod);
        EMAIndicator signal = new EMAIndicator(macd, signalPeriod);
        
        List<Double> macdLine = new ArrayList<>();
        List<Double> signalLine = new ArrayList<>();
        List<Double> histogram = new ArrayList<>();
        
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            double macdVal = macd.getValue(i).isNaN() ? 0 : macd.getValue(i).doubleValue();
            double signalVal = signal.getValue(i).isNaN() ? 0 : signal.getValue(i).doubleValue();
            
            macdLine.add(macdVal);
            signalLine.add(signalVal);
            histogram.add(macdVal - signalVal);
        }
        
        result.put("macd", macdLine);
        result.put("signal", signalLine);
        result.put("histogram", histogram);
        return result;
    }
    
    // ==================== 新增指標 ====================
    
    // 布林通道 (Bollinger Bands) - 返回 [上軌, 中軌, 下軌]
    public Map<String, List<Double>> getBollingerBands(int period, double multiplier) {
        Map<String, List<Double>> result = new HashMap<>();
        
        if (barSeries.getBarCount() < period) {
            result.put("upper", Collections.emptyList());
            result.put("middle", Collections.emptyList());
            result.put("lower", Collections.emptyList());
            return result;
        }
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(new SMAIndicator(closePrice, period));
        StandardDeviationIndicator stdDev = new StandardDeviationIndicator(closePrice, period);
        BollingerBandsUpperIndicator upper = new BollingerBandsUpperIndicator(middle, stdDev, barSeries.numOf(multiplier));
        BollingerBandsLowerIndicator lower = new BollingerBandsLowerIndicator(middle, stdDev, barSeries.numOf(multiplier));
        
        List<Double> upperBand = new ArrayList<>();
        List<Double> middleBand = new ArrayList<>();
        List<Double> lowerBand = new ArrayList<>();
        
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            upperBand.add(upper.getValue(i).isNaN() ? null : upper.getValue(i).doubleValue());
            middleBand.add(middle.getValue(i).isNaN() ? null : middle.getValue(i).doubleValue());
            lowerBand.add(lower.getValue(i).isNaN() ? null : lower.getValue(i).doubleValue());
        }
        
        result.put("upper", upperBand);
        result.put("middle", middleBand);
        result.put("lower", lowerBand);
        return result;
    }
    
    // 隨機指標 (KD / Stochastic Oscillator) - 返回 [K線, D線]
    public Map<String, List<Double>> getStochastic(int kPeriod, int dPeriod) {
        Map<String, List<Double>> result = new HashMap<>();
        
        if (barSeries.getBarCount() < kPeriod) {
            result.put("k", Collections.emptyList());
            result.put("d", Collections.emptyList());
            return result;
        }
        
        StochasticOscillatorKIndicator kIndicator = new StochasticOscillatorKIndicator(barSeries, kPeriod);
        SMAIndicator dIndicator = new SMAIndicator(kIndicator, dPeriod);
        
        List<Double> kLine = new ArrayList<>();
        List<Double> dLine = new ArrayList<>();
        
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            kLine.add(kIndicator.getValue(i).isNaN() ? null : kIndicator.getValue(i).doubleValue());
            dLine.add(dIndicator.getValue(i).isNaN() ? null : dIndicator.getValue(i).doubleValue());
        }
        
        result.put("k", kLine);
        result.put("d", dLine);
        return result;
    }
    
    // 能量潮 (OBV - On Balance Volume)
    public List<Double> getOBV() {
        if (barSeries.getBarCount() < 1) {
            return Collections.emptyList();
        }
        
        OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(barSeries);
        
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            result.add(obv.getValue(i).isNaN() ? null : obv.getValue(i).doubleValue());
        }
        return result;
    }
    
    // ADX (Average Directional Index) - 趨向指標
    public Map<String, List<Double>> getADX(int period) {
        Map<String, List<Double>> result = new HashMap<>();
        
        if (barSeries.getBarCount() < period * 2) {
            result.put("adx", Collections.emptyList());
            result.put("plusDI", Collections.emptyList());
            result.put("minusDI", Collections.emptyList());
            return result;
        }
        
        ADXIndicator adx = new ADXIndicator(barSeries, period);
        PlusDIIndicator plusDI = new PlusDIIndicator(barSeries, period);
        MinusDIIndicator minusDI = new MinusDIIndicator(barSeries, period);
        
        List<Double> adxLine = new ArrayList<>();
        List<Double> plusDILine = new ArrayList<>();
        List<Double> minusDILine = new ArrayList<>();
        
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            adxLine.add(adx.getValue(i).isNaN() ? null : adx.getValue(i).doubleValue());
            plusDILine.add(plusDI.getValue(i).isNaN() ? null : plusDI.getValue(i).doubleValue());
            minusDILine.add(minusDI.getValue(i).isNaN() ? null : minusDI.getValue(i).doubleValue());
        }
        
        result.put("adx", adxLine);
        result.put("plusDI", plusDILine);
        result.put("minusDI", minusDILine);
        return result;
    }
    
    // CCI (Commodity Channel Index) - 順勢指標
    public List<Double> getCCI(int period) {
        if (barSeries.getBarCount() < period) {
            return Collections.emptyList();
        }
        
        CCIIndicator cci = new CCIIndicator(barSeries, period);
        
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            result.add(cci.getValue(i).isNaN() ? null : cci.getValue(i).doubleValue());
        }
        return result;
    }
    
    // Williams %R - 威廉指標
    public List<Double> getWilliamsR(int period) {
        if (barSeries.getBarCount() < period) {
            return Collections.emptyList();
        }
        
        WilliamsRIndicator williamsR = new WilliamsRIndicator(barSeries, period);
        
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            result.add(williamsR.getValue(i).isNaN() ? null : williamsR.getValue(i).doubleValue());
        }
        return result;
    }
    
    // ATR (Average True Range) - 平均真實範圍
    public List<Double> getATR(int period) {
        if (barSeries.getBarCount() < period) {
            return Collections.emptyList();
        }
        
        ATRIndicator atr = new ATRIndicator(barSeries, period);
        
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            result.add(atr.getValue(i).isNaN() ? null : atr.getValue(i).doubleValue());
        }
        return result;
    }
    
    // 成交量移動平均
    public List<Double> getVolumeMA(int period) {
        if (barSeries.getBarCount() < period) {
            return Collections.emptyList();
        }
        
        VolumeIndicator volume = new VolumeIndicator(barSeries);
        SMAIndicator volumeMA = new SMAIndicator(volume, period);
        
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            result.add(volumeMA.getValue(i).isNaN() ? null : volumeMA.getValue(i).doubleValue());
        }
        return result;
    }
    
    /**
     * 清空所有數據
     */
    public void clearAllData() {
        barSeries = new BaseBarSeries("Market Data");
        barSeries.setMaximumBarCount(maxBars);
    }
}

