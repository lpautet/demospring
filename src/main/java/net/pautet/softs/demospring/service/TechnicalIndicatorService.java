package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Technical Indicator Service
 * Calculates trading indicators: RSI, MACD, Bollinger Bands, Moving Averages, ATR, VWAP
 */
@Service
@Slf4j
public class TechnicalIndicatorService {

    private final BinanceApiService binanceApiService;
    private final net.pautet.softs.demospring.config.BinanceConfig binanceConfig;

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

            // Extract OHLCV data directly from BinanceKline - no intermediate conversion needed!
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
            double rsi = calculateRSI(closes, 14);
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
            Map<String, Double> bb = calculateBollingerBands(closes, 20, 2.0);
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
            double atr = calculateATR(highs, lows, closes, 14);
            analysis.put("atr", atr);
            analysis.put("atrPercent", (atr / currentPrice) * 100);

            // VWAP
            double vwap = calculateVWAP(highs, lows, closes, volumes);
            double vwapDistanceValue = ((currentPrice - vwap) / vwap) * 100;
            analysis.put("vwap", vwap);
            analysis.put("vwapDistance", vwapDistanceValue);
            analysis.put("priceVsVWAP", vwapDistanceValue); // Alias
            analysis.put("vwapSignal", currentPrice > vwap ? "BULLISH" : "BEARISH");

            // Stochastic Oscillator
            Map<String, Double> stochastic = calculateStochastic(highs, lows, closes, 14);
            analysis.put("stochK", stochastic.get("k"));
            analysis.put("stochD", stochastic.get("d"));
            analysis.put("stochasticSignal", getStochasticSignal(stochastic));

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
     * Handles limited data gracefully
     */
    private Map<String, Double> calculateMACD(double[] prices) {
        Map<String, Double> result = new LinkedHashMap<>();
        
        // MACD needs at least 26+ data points for EMA26
        if (prices.length < 12) {
            // Not enough for even EMA12
            result.put("macdLine", 0.0);
            result.put("signalLine", 0.0);
            result.put("histogram", 0.0);
            return result;
        }
        
        // Use shorter periods if not enough data
        int period1 = Math.min(12, prices.length - 2);
        int period2 = Math.min(26, prices.length - 2);
        
        double[] ema12 = calculateEMAArray(prices, period1);
        double[] ema26 = calculateEMAArray(prices, period2);

        double macdLine = ema12[ema12.length - 1] - ema26[ema26.length - 1];
        
        // Simplified signal line (would normally use EMA of MACD)
        if (ema12.length > 1 && ema26.length > 1) {
            double prevMacd = ema12[ema12.length - 2] - ema26[ema26.length - 2];
            double histogram = macdLine - prevMacd;
            
            result.put("macdLine", macdLine);
            result.put("signalLine", prevMacd);
            result.put("histogram", histogram);
        } else {
            result.put("macdLine", macdLine);
            result.put("signalLine", 0.0);
            result.put("histogram", 0.0);
        }
        
        return result;
    }

    private String getMACDSignal(Map<String, Double> macd) {
        double histogram = macd.get("histogram");
        if (histogram > 10) return "STRONG_BULLISH_CROSSOVER";
        if (histogram > 0) return "BULLISH";
        if (histogram < -10) return "STRONG_BEARISH_CROSSOVER";
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
     */
    private Map<String, Double> calculateStochastic(double[] highs, double[] lows, double[] closes, int period) {
        if (closes.length < period) {
            return Map.of("k", 50.0, "d", 50.0);
        }

        double lowest = Double.MAX_VALUE;
        double highest = Double.MIN_VALUE;

        for (int i = closes.length - period; i < closes.length; i++) {
            if (lows[i] < lowest) lowest = lows[i];
            if (highs[i] > highest) highest = highs[i];
        }

        double currentClose = closes[closes.length - 1];
        double k = ((currentClose - lowest) / (highest - lowest)) * 100;
        double d = k; // Simplified, normally would be SMA of K

        return Map.of("k", k, "d", d);
    }

    private String getStochasticSignal(Map<String, Double> stoch) {
        double k = stoch.get("k");
        if (k < 20) return "OVERSOLD_BUY";
        if (k > 80) return "OVERBOUGHT_SELL";
        return "NEUTRAL";
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

        // Start with SMA
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += prices[i];
        }
        
        int startIndex = period - 1;
        if (startIndex < prices.length) {
            ema[startIndex] = sum / period;
        }

        // Calculate EMA
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
            (String) analysis.get("macdSignalText"),  // Fixed: use text signal, not numerical
            (String) analysis.get("movingAverageSignal"),
            (String) analysis.get("bollingerSignal"),
            (String) analysis.get("vwapSignal"),
            (String) analysis.get("stochasticSignal")
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
        
        if (buySignals >= 4) {
            overall = "STRONG_BUY";
            confidence = "VERY_HIGH";
        } else if (buySignals >= 3) {
            overall = "BUY";
            confidence = "HIGH";
        } else if (sellSignals >= 4) {
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
}
