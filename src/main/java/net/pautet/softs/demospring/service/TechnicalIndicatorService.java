package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.BinanceKline;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Technical Indicator Service
 * Calculates trading indicators: RSI, MACD, Bollinger Bands, Moving Averages, ATR, VWAP, CCI, Williams %R
 */
@Service
@Slf4j
public class TechnicalIndicatorService {

    private final BinanceApiService binanceApiService;
    private final net.pautet.softs.demospring.config.BinanceConfig binanceConfig;

    // Constants for Indicator Periods
    private static final int RSI_PERIOD = 14;
    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;
    private static final int MACD_SIGNAL = 9;
    private static final int BOLLINGER_PERIOD = 20;
    private static final double BOLLINGER_MULTIPLIER = 2.0;
    private static final int STOCHASTIC_PERIOD = 14;
    private static final int CCI_PERIOD = 20;
    private static final int WILLIAMS_R_PERIOD = 14;
    private static final int ATR_PERIOD = 14;

    public TechnicalIndicatorService(BinanceApiService binanceApiService, 
                                    net.pautet.softs.demospring.config.BinanceConfig binanceConfig) {
        this.binanceApiService = binanceApiService;
        this.binanceConfig = binanceConfig;
    }

    /**
     * Calculate comprehensive technical analysis for a symbol and timeframe
     * 
     * @param symbol Trading pair (e.g., "ETHUSDC", "BTCUSDT")
     * @param interval Timeframe (e.g., "5m", "15m", "1h")
     * @param limit Number of candles to analyze
     */
    public Map<String, Object> calculateIndicators(String symbol, String interval, int limit) {
        try {
            List<net.pautet.softs.demospring.dto.BinanceKline> klines = binanceApiService.getKlines(symbol, interval, limit);
            
            log.debug("Fetched {} klines for {} {} interval", klines.size(), symbol, interval);
            
            // Production requires reliable data, testnet is more lenient
            boolean isTestnet = binanceConfig.isTestnet();
            int minimumRequired = isTestnet ? 3 : 50;
            
            if (klines.size() < minimumRequired) {
                String environment = isTestnet ? "testnet" : "production";
                String explanation = isTestnet 
                    ? "Testnet was recently reset - historical data is accumulating. Current indicators will have limited accuracy."
                    : "Production trading requires at least 50 candles for reliable technical analysis. Please wait for more data to accumulate or use a different timeframe.";
                
                log.warn("Insufficient klines for {}: got {} but need {}+ for {} interval", 
                    environment, klines.size(), minimumRequired, interval);
                
                return Map.of(
                    "error", "Insufficient data for analysis", 
                    "candlesReceived", klines.size(),
                    "candlesRequired", minimumRequired,
                    "environment", environment,
                    "explanation", explanation,
                    "recommendation", klines.size() < 3 
                        ? "Wait for more trading activity to accumulate data"
                        : (isTestnet ? "Indicators available but with limited accuracy" : "Use 5m interval which typically has more data")
                );
            }
            
            // Warn about limited data quality
            if (klines.size() < 20) {
                log.warn("Limited data: got {} candles for {} interval - indicators may be less accurate", klines.size(), interval);
            } else if (klines.size() < 50) {
                log.info("Fair data: got {} candles for {} interval - indicators should be reasonably accurate", klines.size(), interval);
            } else {
                log.debug("Good data: calculating indicators with {} candles for {} interval", klines.size(), interval);
            }

            // Extract OHLCV data directly from BinanceKline
            double[] closes = klines.stream().mapToDouble(net.pautet.softs.demospring.dto.BinanceKline::closeAsDouble).toArray();
            double[] highs = klines.stream().mapToDouble(net.pautet.softs.demospring.dto.BinanceKline::highAsDouble).toArray();
            double[] lows = klines.stream().mapToDouble(net.pautet.softs.demospring.dto.BinanceKline::lowAsDouble).toArray();
            double[] volumes = klines.stream().mapToDouble(net.pautet.softs.demospring.dto.BinanceKline::volumeAsDouble).toArray();

            Map<String, Object> analysis = new LinkedHashMap<>();
            
            // Current price
            double currentPrice = closes[closes.length - 1];
            analysis.put("currentPrice", currentPrice);
            analysis.put("timeframe", interval);
            analysis.put("dataPoints", closes.length);
            
            // Add data quality indicator
            String dataQuality = closes.length >= 50 ? "GOOD" : 
                               closes.length >= 20 ? "FAIR" : "LIMITED";
            analysis.put("dataQuality", dataQuality);

            // Momentum Indicators
            // RSI
            double rsi = calculateRSI(closes, RSI_PERIOD);
            analysis.put("rsi", rsi);
            analysis.put("rsiSignal", getRSISignal(rsi));

            // MACD
            Map<String, Double> macd = calculateMACD(closes);
            analysis.put("macd", macd.get("macdLine"));
            analysis.put("macdSignal", macd.get("signalLine")); // This is the numerical signal line
            analysis.put("macdSignalLine", macd.get("signalLine")); // Alias for QuickRecommendationService
            analysis.put("macdHistogram", macd.get("histogram"));
            analysis.put("macdSignalText", getMACDSignal(macd)); // This is the text signal (BULLISH/BEARISH)

            // Moving Averages (use smaller periods if not enough data)
            int dataSize = closes.length;
            double sma20 = dataSize >= 20 ? calculateSMA(closes, 20) : calculateSMA(closes, Math.min(dataSize, 10));
            double sma50 = dataSize >= 50 ? calculateSMA(closes, 50) : 0.0;
            double ema12 = dataSize >= 12 ? calculateEMA(closes, 12) : calculateEMA(closes, Math.min(dataSize, 5));
            double ema20 = dataSize >= 20 ? calculateEMA(closes, 20) : calculateEMA(closes, Math.min(dataSize, 10));
            double ema26 = dataSize >= 26 ? calculateEMA(closes, 26) : 0.0;
            double ema50 = dataSize >= 50 ? calculateEMA(closes, 50) : 0.0;
            
            analysis.put("sma20", sma20);
            analysis.put("sma50", sma50);
            analysis.put("ema12", ema12);
            analysis.put("ema20", ema20);
            analysis.put("ema26", ema26);
            analysis.put("ema50", ema50);
            analysis.put("priceVsSMA20", ((currentPrice - sma20) / sma20) * 100);
            analysis.put("priceVsSMA50", ((currentPrice - sma50) / sma50) * 100);
            
            String maSignal = "NEUTRAL";
            if (currentPrice > sma20 && sma20 > sma50) {
                maSignal = "BULLISH_GOLDEN_CROSS";
            } else if (currentPrice < sma20 && sma20 < sma50) {
                maSignal = "BEARISH_DEATH_CROSS";
            } else if (currentPrice > sma20) {
                maSignal = "BULLISH";
            } else if (currentPrice < sma20) {
                maSignal = "BEARISH";
            }
            analysis.put("movingAverageSignal", maSignal);

            // Bollinger Bands
            Map<String, Double> bb = calculateBollingerBands(closes, BOLLINGER_PERIOD, BOLLINGER_MULTIPLIER);
            double bbUpper = bb.get("upper");
            double bbMiddle = bb.get("middle");
            double bbLower = bb.get("lower");
            
            analysis.put("bbUpper", bbUpper);
            analysis.put("bbMiddle", bbMiddle);
            analysis.put("bbLower", bbLower);
            analysis.put("bbBandwidth", bb.get("bandwidth"));
            analysis.put("bollingerSignal", getBollingerSignal(currentPrice, bb));
            
            // Aliases for QuickRecommendationService
            analysis.put("bollingerUpper", bbUpper);
            analysis.put("bollingerMiddle", bbMiddle);
            analysis.put("bollingerLower", bbLower);
            
            // Calculate Bollinger position
            String bollingerPosition = "MIDDLE";
            double bollingerPercentage = ((currentPrice - bbMiddle) / bbMiddle) * 100;
            if (currentPrice >= bbUpper) bollingerPosition = "UPPER_BAND";
            else if (currentPrice <= bbLower) bollingerPosition = "LOWER_BAND";
            else if (currentPrice > bbMiddle) bollingerPosition = "ABOVE_MIDDLE";
            else if (currentPrice < bbMiddle) bollingerPosition = "BELOW_MIDDLE";
            
            analysis.put("bollingerPosition", bollingerPosition);
            analysis.put("bollingerPercentage", bollingerPercentage);

            // ATR (Volatility)
            double atr = calculateATR(highs, lows, closes, ATR_PERIOD);
            analysis.put("atr", atr);
            analysis.put("atrPercent", (atr / currentPrice) * 100);

            // ADX, +DI, -DI and BB Width %
            double adx = 0.0;
            double plusDi = 0.0;
            double minusDi = 0.0;
            double bbWidthPct = 0.0;

            // Only calculate ADX if we have enough data (minimum 28 candles for reliable ADX)
            if (closes.length >= 28) {
                Map<String, Double> adxResult = calculateADX(highs, lows, closes, 14);
                adx     = adxResult.get("adx");
                plusDi  = adxResult.get("plusDi");
                minusDi = adxResult.get("minusDi");
            }

            analysis.put("adx",     round(adx,     2));
            analysis.put("plusDi",  round(plusDi,  2));
            analysis.put("minusDi", round(minusDi, 2));

            // Bollinger Band Width %
            if (bbMiddle != 0.0) {
                bbWidthPct = ((bbUpper - bbLower) / bbMiddle) * 100.0;
            }
            analysis.put("bbWidthPct", round(bbWidthPct, 2));

            // Also store current closed price (not the live incomplete candle)
            double lastClosedPrice = closes[closes.length - 2 >= 0 ? closes.length - 2 : 0];
            analysis.put("price", round(lastClosedPrice, 2));
            
            // VWAP
            double vwap = calculateVWAP(highs, lows, closes, volumes);
            double vwapDistanceValue = ((lastClosedPrice - vwap) / vwap) * 100;
            analysis.put("vwap", vwap);
            analysis.put("vwapDistance", vwapDistanceValue);
            analysis.put("priceVsVWAP", vwapDistanceValue);
            analysis.put("vwapSignal", lastClosedPrice > vwap ? "BULLISH" : "BEARISH");

            // Stochastic Oscillator
            Map<String, Double> stochastic = calculateStochastic(highs, lows, closes, STOCHASTIC_PERIOD);
            analysis.put("stochK", stochastic.get("k"));
            analysis.put("stochD", stochastic.get("d"));
            analysis.put("stochasticSignal", getStochasticSignal(stochastic));

            // === NEW INDICATORS ===

            // Commodity Channel Index (CCI)
            double cci = calculateCCI(highs, lows, closes, CCI_PERIOD);
            analysis.put("cci", cci);
            analysis.put("cciSignal", getCCISignal(cci));

            // Williams %R
            double williamsR = calculateWilliamsR(highs, lows, closes, WILLIAMS_R_PERIOD);
            analysis.put("williamsR", williamsR);
            analysis.put("williamsRSignal", getWilliamsRSignal(williamsR));

            // Pivot Points (Standard) based on previous completed candle
            // Using last closed candle (index length-2) to avoid repainting with current live candle
            int lastClosedIdx = Math.max(0, closes.length - 2);
            Map<String, Double> pivots = calculatePivotPoints(
                highs[lastClosedIdx], 
                lows[lastClosedIdx], 
                closes[lastClosedIdx]
            );
            analysis.put("pivotPoints", pivots);

            // Overall signal aggregation
            analysis.put("overallSignal", aggregateSignals(analysis));

            return analysis;

        } catch (Exception e) {
            log.error("Error calculating technical indicators", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * RSI (Relative Strength Index)
     * Range: 0-100
     * < 30: Oversold (BUY)
     * > 70: Overbought (SELL)
     */
    private double calculateRSI(double[] prices, int period) {
        if (prices.length < period + 1) return 50.0;

        double gains = 0.0;
        double losses = 0.0;

        for (int i = prices.length - period; i < prices.length; i++) {
            double change = prices[i] - prices[i - 1];
            if (change > 0) gains += change;
            else losses += Math.abs(change);
        }

        double avgGain = gains / period;
        double avgLoss = losses / period;

        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private String getRSISignal(double rsi) {
        if (rsi < 25) return "VERY_OVERSOLD_STRONG_BUY";
        if (rsi < 30) return "OVERSOLD_BUY";
        if (rsi < 40) return "SLIGHTLY_OVERSOLD";
        if (rsi > 75) return "VERY_OVERBOUGHT_STRONG_SELL";
        if (rsi > 70) return "OVERBOUGHT_SELL";
        if (rsi > 60) return "SLIGHTLY_OVERBOUGHT";
        return "NEUTRAL";
    }

    /**
     * MACD (Moving Average Convergence Divergence)
     * Standard: 12, 26, 9
     */
    private Map<String, Double> calculateMACD(double[] prices) {
        Map<String, Double> result = new LinkedHashMap<>();

        // MACD needs at least 26 data points
        if (prices.length < MACD_SLOW) {
            result.put("macdLine", 0.0);
            result.put("signalLine", 0.0);
            result.put("histogram", 0.0);
            return result;
        }

        // 1. Calculate MACD Line (EMA12 - EMA26)
        double[] emaFast = calculateEMAArray(prices, MACD_FAST);
        double[] emaSlow = calculateEMAArray(prices, MACD_SLOW);
        
        double[] macdLine = new double[prices.length];
        for (int i = 0; i < prices.length; i++) {
            macdLine[i] = emaFast[i] - emaSlow[i];
        }

        // 2. Calculate Signal Line (9-period EMA of MACD Line)
        double[] signalLineArr = calculateEMAArray(macdLine, MACD_SIGNAL);

        // 3. Get latest values
        double finalMacd = macdLine[prices.length - 1];
        double finalSignal = signalLineArr[prices.length - 1];
        double histogram = finalMacd - finalSignal;

        result.put("macdLine", finalMacd);
        result.put("signalLine", finalSignal);
        result.put("histogram", histogram);

        return result;
    }

    private String getMACDSignal(Map<String, Double> macd) {
        double histogram = macd.get("histogram");
        // Simple heuristic based on histogram
        if (histogram > 0.5) return "STRONG_BULLISH_CROSSOVER";
        if (histogram > 0) return "BULLISH";
        if (histogram < -0.5) return "STRONG_BEARISH_CROSSOVER";
        if (histogram < 0) return "BEARISH";
        return "NEUTRAL";
    }

    /**
     * Bollinger Bands
     */
    private Map<String, Double> calculateBollingerBands(double[] prices, int period, double stdDevMultiplier) {
        double sma = calculateSMA(prices, period);
        double stdDev = calculateStdDev(prices, period);

        Map<String, Double> bands = new LinkedHashMap<>();
        bands.put("upper", sma + (stdDevMultiplier * stdDev));
        bands.put("middle", sma);
        bands.put("lower", sma - (stdDevMultiplier * stdDev));
        bands.put("bandwidth", ((bands.get("upper") - bands.get("lower")) / sma) * 100);
        return bands;
    }

    private String getBollingerSignal(double price, Map<String, Double> bb) {
        double upper = bb.get("upper");
        double lower = bb.get("lower");
        double middle = bb.get("middle");

        if (price <= lower) return "OVERSOLD_AT_LOWER_BAND_BUY";
        if (price >= upper) return "OVERBOUGHT_AT_UPPER_BAND_SELL";
        if (price < middle) return "BELOW_MIDDLE_BEARISH";
        if (price > middle) return "ABOVE_MIDDLE_BULLISH";
        return "AT_MIDDLE_NEUTRAL";
    }

    /**
     * ATR (Average True Range) - Volatility
     */
    private double calculateATR(double[] highs, double[] lows, double[] closes, int period) {
        if (highs.length < period + 1) return 0.0;

        double[] tr = new double[highs.length];
        for (int i = 1; i < highs.length; i++) {
            double hl = highs[i] - lows[i];
            double hc = Math.abs(highs[i] - closes[i - 1]);
            double lc = Math.abs(lows[i] - closes[i - 1]);
            tr[i] = Math.max(hl, Math.max(hc, lc));
        }

        return calculateSMA(tr, period);
    }

    /**
     * VWAP (Volume Weighted Average Price)
     */
    private double calculateVWAP(double[] highs, double[] lows, double[] closes, double[] volumes) {
        double cumVolume = 0;
        double cumVolumePrice = 0;

        for (int i = 0; i < closes.length; i++) {
            double typical = (highs[i] + lows[i] + closes[i]) / 3.0;
            cumVolumePrice += typical * volumes[i];
            cumVolume += volumes[i];
        }

        return cumVolume > 0 ? cumVolumePrice / cumVolume : closes[closes.length - 1];
    }

    /**
     * Stochastic Oscillator
     * %K = (Current Close - Lowest Low) / (Highest High - Lowest Low) * 100
     * %D = 3-day SMA of %K
     */
    private Map<String, Double> calculateStochastic(double[] highs, double[] lows, double[] closes, int period) {
        if (closes.length < period) {
            return Map.of("k", 50.0, "d", 50.0);
        }

        double[] kSeries = new double[closes.length];

        for (int i = 0; i < closes.length; i++) {
            if (i < period - 1) {
                kSeries[i] = 50.0; // Not enough data
                continue;
            }
            
            double lowest = Double.MAX_VALUE;
            double highest = Double.MIN_VALUE;

            // Look back 'period' candles (inclusive of current)
            for (int j = i - period + 1; j <= i; j++) {
                if (lows[j] < lowest) lowest = lows[j];
                if (highs[j] > highest) highest = highs[j];
            }

            if (highest == lowest) {
                kSeries[i] = 50.0;
            } else {
                kSeries[i] = ((closes[i] - lowest) / (highest - lowest)) * 100;
            }
        }

        double k = kSeries[closes.length - 1];
        // %D is usually a 3-period SMA of %K
        double d = calculateSMA(kSeries, 3);

        return Map.of("k", k, "d", d);
    }

    private String getStochasticSignal(Map<String, Double> stoch) {
        double k = stoch.get("k");
        if (k < 20) return "OVERSOLD_BUY";
        if (k > 80) return "OVERBOUGHT_SELL";
        return "NEUTRAL";
    }

    /**
     * Commodity Channel Index (CCI)
     * Measures the difference between the current price and the historical average price.
     * > 100: Overbought
     * < -100: Oversold
     */
    private double calculateCCI(double[] highs, double[] lows, double[] closes, int period) {
        if (closes.length < period) return 0.0;

        // Calculate Typical Price (TP)
        double[] tp = new double[closes.length];
        for (int i = 0; i < closes.length; i++) {
            tp[i] = (highs[i] + lows[i] + closes[i]) / 3.0;
        }

        // Calculate Simple Moving Average of TP
        double smaTp = calculateSMA(tp, period);

        // Calculate Mean Deviation
        double meanDev = 0.0;
        int startIndex = Math.max(0, tp.length - period);
        for (int i = startIndex; i < tp.length; i++) {
            meanDev += Math.abs(tp[i] - smaTp);
        }
        meanDev /= period;

        // Avoid division by zero
        if (meanDev == 0) return 0.0;

        // CCI Calculation
        // Use the most recent Typical Price
        double currentTp = tp[tp.length - 1];
        return (currentTp - smaTp) / (0.015 * meanDev);
    }

    private String getCCISignal(double cci) {
        if (cci < -100) return "OVERSOLD_BUY";
        if (cci > 100) return "OVERBOUGHT_SELL";
        return "NEUTRAL";
    }

    /**
     * Williams %R
     * Momentum indicator that moves between 0 and -100.
     * > -20: Overbought
     * < -80: Oversold
     */
    private double calculateWilliamsR(double[] highs, double[] lows, double[] closes, int period) {
        if (closes.length < period) return -50.0;

        double currentClose = closes[closes.length - 1];
        double highestHigh = Double.MIN_VALUE;
        double lowestLow = Double.MAX_VALUE;

        // Look back 'period' candles
        for (int i = closes.length - period; i < closes.length; i++) {
            if (highs[i] > highestHigh) highestHigh = highs[i];
            if (lows[i] < lowestLow) lowestLow = lows[i];
        }

        if (highestHigh == lowestLow) return -50.0;

        return ((highestHigh - currentClose) / (highestHigh - lowestLow)) * -100.0;
    }

    private String getWilliamsRSignal(double williamsR) {
        if (williamsR < -80) return "OVERSOLD_BUY";
        if (williamsR > -20) return "OVERBOUGHT_SELL";
        return "NEUTRAL";
    }

    /**
     * Standard Pivot Points
     * Calculated on the given High, Low, Close (usually of the previous period)
     */
    private Map<String, Double> calculatePivotPoints(double high, double low, double close) {
        double p = (high + low + close) / 3.0;
        double r1 = (2 * p) - low;
        double s1 = (2 * p) - high;
        double r2 = p + (high - low);
        double s2 = p - (high - low);
        double r3 = high + 2 * (p - low);
        double s3 = low - 2 * (high - p);

        Map<String, Double> pivots = new LinkedHashMap<>();
        pivots.put("pivot", round(p, 2));
        pivots.put("r1", round(r1, 2));
        pivots.put("s1", round(s1, 2));
        pivots.put("r2", round(r2, 2));
        pivots.put("s2", round(s2, 2));
        pivots.put("r3", round(r3, 2));
        pivots.put("s3", round(s3, 2));
        
        return pivots;
    }

    /**
     * Simple Moving Average
     */
    private double calculateSMA(double[] prices, int period) {
        // Handle insufficient data - use all available
        if (prices.length < period) {
            period = prices.length;
        }

        double sum = 0;
        int startIndex = Math.max(0, prices.length - period);
        for (int i = startIndex; i < prices.length; i++) {
            sum += prices[i];
        }
        return sum / period;
    }

    /**
     * Exponential Moving Average
     */
    private double calculateEMA(double[] prices, int period) {
        double[] ema = calculateEMAArray(prices, period);
        return ema[ema.length - 1];
    }

    private double[] calculateEMAArray(double[] prices, int period) {
        double[] ema = new double[prices.length];
        
        // Handle insufficient data
        if (prices.length < period) {
            // Use all available data for initial SMA
            period = prices.length;
        }
        
        double multiplier = 2.0 / (period + 1);

        // Start with SMA for the first valid point
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += prices[i];
        }
        
        // Fill initial values (optional, but good for visualization if needed)
        // For strict EMA calculation, the first valid EMA is at index (period - 1)
        int startIndex = period - 1;
        ema[startIndex] = sum / period;

        // Propagate EMA
        for (int i = period; i < prices.length; i++) {
            ema[i] = (prices[i] - ema[i - 1]) * multiplier + ema[i - 1];
        }

        return ema;
    }

    /**
     * Standard Deviation
     */
    private double calculateStdDev(double[] prices, int period) {
        // Handle insufficient data
        if (prices.length < period) {
            period = prices.length;
        }
        
        double sma = calculateSMA(prices, period);
        double sumSquaredDiff = 0;

        int startIndex = Math.max(0, prices.length - period);
        for (int i = startIndex; i < prices.length; i++) {
            double diff = prices[i] - sma;
            sumSquaredDiff += diff * diff;
        }

        return Math.sqrt(sumSquaredDiff / period);
    }

    /**
     * Aggregate all signals into overall assessment
     */
    private Map<String, Object> aggregateSignals(Map<String, Object> analysis) {
        int buySignals = 0;
        int sellSignals = 0;
        int neutralSignals = 0;

        List<String> signals = Arrays.asList(
            (String) analysis.get("rsiSignal"),
            (String) analysis.get("macdSignalText"),
            (String) analysis.get("movingAverageSignal"),
            (String) analysis.get("bollingerSignal"),
            (String) analysis.get("vwapSignal"),
            (String) analysis.get("stochasticSignal"),
            (String) analysis.get("cciSignal"),
            (String) analysis.get("williamsRSignal")
        );

        for (String signal : signals) {
            if (signal.contains("BUY") || signal.contains("BULLISH")) {
                buySignals++;
            } else if (signal.contains("SELL") || signal.contains("BEARISH")) {
                sellSignals++;
            } else {
                neutralSignals++;
            }
        }

        String overall;
        String confidence;
        
        // Adjusted thresholds for 8 indicators
        if (buySignals >= 5) {
            overall = "STRONG_BUY";
            confidence = "VERY_HIGH";
        } else if (buySignals >= 3) {
            overall = "BUY";
            confidence = "HIGH";
        } else if (sellSignals >= 5) {
            overall = "STRONG_SELL";
            confidence = "VERY_HIGH";
        } else if (sellSignals >= 3) {
            overall = "SELL";
            confidence = "HIGH";
        } else {
            overall = "HOLD";
            confidence = "LOW";
        }

        return Map.of(
            "signal", overall,
            "confidence", confidence,
            "buySignals", buySignals,
            "sellSignals", sellSignals,
            "neutralSignals", neutralSignals
        );
    }

    // Volume Ratio
    public double getVolumeRatio5m(String symbol) {
        try {
            List<BinanceKline> klines = binanceApiService.getKlines(
                    symbol, "5m", 21);

            if (klines.size() < 21) return 1.0;

            double currentVolume = klines.get(klines.size() - 1).volumeAsDouble(); // incomplete candle
            double avgVolume = klines.subList(0, 20).stream()
                    .mapToDouble(BinanceKline::volumeAsDouble)
                    .average()
                    .orElse(1.0);

            return currentVolume / avgVolume;

        } catch (Exception e) {
            log.warn("Failed to get volume ratio for {}", symbol, e);
            return 1.0;
        }
    }

    /**
     * Calculate ADX, +DI, -DI using Wilder's Smoothing
     */
    private Map<String, Double> calculateADX(double[] high, double[] low, double[] close, int period) {
        int len = close.length;
        if (len < period * 2) {
            return Map.of("adx", 0.0, "plusDi", 0.0, "minusDi", 0.0);
        }

        double[] tr = new double[len];
        double[] plusDm = new double[len];
        double[] minusDm = new double[len];

        // 1. Calculate TR, +DM, -DM
        tr[0] = high[0] - low[0]; // First TR is High - Low
        for (int i = 1; i < len; i++) {
            double h = high[i];
            double l = low[i];
            double cPrev = close[i - 1];

            double val1 = h - l;
            double val2 = Math.abs(h - cPrev);
            double val3 = Math.abs(l - cPrev);
            tr[i] = Math.max(val1, Math.max(val2, val3));

            double up = h - high[i - 1];
            double down = low[i - 1] - l;

            plusDm[i] = (up > down && up > 0) ? up : 0.0;
            minusDm[i] = (down > up && down > 0) ? down : 0.0;
        }

        // 2. Smooth TR, +DM, -DM using Wilder's Smoothing
        // First value is sum of first 'period' values
        double[] smoothTr = new double[len];
        double[] smoothPlusDm = new double[len];
        double[] smoothMinusDm = new double[len];

        for (int i = 0; i < period; i++) {
            smoothTr[period - 1] += tr[i];
            smoothPlusDm[period - 1] += plusDm[i];
            smoothMinusDm[period - 1] += minusDm[i];
        }

        for (int i = period; i < len; i++) {
            smoothTr[i] = smoothTr[i - 1] - (smoothTr[i - 1] / period) + tr[i];
            smoothPlusDm[i] = smoothPlusDm[i - 1] - (smoothPlusDm[i - 1] / period) + plusDm[i];
            smoothMinusDm[i] = smoothMinusDm[i - 1] - (smoothMinusDm[i - 1] / period) + minusDm[i];
        }

        // 3. Calculate +DI, -DI, DX
        double[] dx = new double[len];
        for (int i = period - 1; i < len; i++) {
            double pDi = 0;
            double mDi = 0;
            if (smoothTr[i] != 0) {
                pDi = (smoothPlusDm[i] / smoothTr[i]) * 100;
                mDi = (smoothMinusDm[i] / smoothTr[i]) * 100;
            }
            double sum = pDi + mDi;
            dx[i] = (sum == 0) ? 0 : (Math.abs(pDi - mDi) / sum) * 100;
        }

        // 4. Calculate ADX (Smoothed DX)
        // First ADX is average of first 'period' DX values.
        // The first valid DX is at index 'period - 1'.
        // So we need DX from [period - 1] to [period * 2 - 2].
        
        // We need at least 'period' count of DX values to start ADX.
        // dx has valid values starting at index 'period-1'.
        
        double firstAdxSum = 0;
        int dxStartIndex = period - 1;
        int adxStartIndex = dxStartIndex + period - 1; 

        if (len <= adxStartIndex) {
             return Map.of("adx", 0.0, "plusDi", 0.0, "minusDi", 0.0);
        }

        for (int i = dxStartIndex; i < dxStartIndex + period; i++) {
            firstAdxSum += dx[i];
        }
        
        double adx = firstAdxSum / period; // Initial ADX
        
        // Smoothing subsequent ADX values
        for (int i = dxStartIndex + period; i < len; i++) {
            adx = ((adx * (period - 1)) + dx[i]) / period;
        }

        // Return latest values
        double lastTr = smoothTr[len - 1];
        double lastPlus = smoothPlusDm[len - 1];
        double lastMinus = smoothMinusDm[len - 1];
        
        double finalPlusDi = lastTr == 0 ? 0 : (lastPlus / lastTr) * 100;
        double finalMinusDi = lastTr == 0 ? 0 : (lastMinus / lastTr) * 100;

        return Map.of(
                "adx",     round(adx, 2),
                "plusDi",  round(finalPlusDi, 2),
                "minusDi", round(finalMinusDi, 2)
        );
    }

    private double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}