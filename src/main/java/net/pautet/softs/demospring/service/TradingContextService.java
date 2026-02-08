package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.AccountSummary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized service for gathering trading context
 * Single source of truth for all context data passed to AI, displayed in /eth context, etc.
 */
@Service
@Slf4j
public class TradingContextService {

    private final BinanceApiService binanceApiService;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final SentimentAnalysisService sentimentAnalysisService;
    private final BinanceTradingService tradingService;
    private final TradingMemoryService tradingMemoryService;

    public TradingContextService(BinanceApiService binanceApiService,
                                TechnicalIndicatorService technicalIndicatorService,
                                SentimentAnalysisService sentimentAnalysisService,
                                BinanceTradingService tradingService,
                                TradingMemoryService tradingMemoryService) {
        this.binanceApiService = binanceApiService;
        this.technicalIndicatorService = technicalIndicatorService;
        this.sentimentAnalysisService = sentimentAnalysisService;
        this.tradingService = tradingService;
        this.tradingMemoryService = tradingMemoryService;
    }

    /**
     * Gather complete trading context
     * Returns raw data objects for flexible use
     */
    public TradingContext gatherCompleteContext() {
        TradingContext context = new TradingContext();

        try {
            // 1. Market Data
            context.ticker = binanceApiService.get24hrTicker(BinanceApiService.ETHUSDC);

            // 2. Technical Indicators
            context.tech5m = technicalIndicatorService.calculateIndicators(
                    BinanceTradingService.SYMBOL_ETHUSDC, "5m", 100);
            context.tech15m = technicalIndicatorService.calculateIndicators(
                    BinanceTradingService.SYMBOL_ETHUSDC, "15m", 100);
            context.tech1h = technicalIndicatorService.calculateIndicators(
                    BinanceTradingService.SYMBOL_ETHUSDC, "1h", 200);

            // 3. Sentiment
            context.sentiment = sentimentAnalysisService.getMarketSentiment(
                    BinanceTradingService.SYMBOL_ETHUSDC);

            // 4. Portfolio
            context.portfolio = tradingService.getAccountSummary();

            // 5. Trading Memory
            context.tradingMemory = tradingMemoryService.getTradingMemoryContext();

            log.debug("Trading context gathered successfully");
            return context;

        } catch (Exception e) {
            log.error("Error gathering trading context", e);
            throw new RuntimeException("Failed to gather trading context", e);
        }
    }

    /**
     * Format context for AI prompt (String format)
     * Used by QuickRecommendationService for LLM input
     */
    public Map<String, String> formatForPrompt() {
        TradingContext context = gatherCompleteContext();
        Map<String, String> formatted = new HashMap<>();

        long nowUtcEpochSeconds = Instant.now().getEpochSecond();

        // 1. MARKET DATA — now with data age, spread, candle timing, VWAP, volume ratio
        double price = context.ticker.lastPriceAsDouble();
        double bid = context.ticker.bidPriceAsDouble();
        double ask = context.ticker.askPriceAsDouble();
        double spreadUsd = ask - bid;
        double spreadBps = (spreadUsd / price) * 10_000;

        // You need to add these two lines in BinanceApiService or calculate from klines
        double sessionVwap = binanceApiService.getSessionVwap(); // today's VWAP from 00:00 UTC
        double current5mVolumeRatio = technicalIndicatorService.getVolumeRatio5m(BinanceTradingService.SYMBOL_ETHUSDC); // current 5m vol / avg20

        // Candle timing
        long fiveMin = nowUtcEpochSeconds - (nowUtcEpochSeconds % 300);
        long fifteenMin = nowUtcEpochSeconds - (nowUtcEpochSeconds % 900);
        long oneHour = nowUtcEpochSeconds - (nowUtcEpochSeconds % 3600);

        int secLeft5m = (int) (300 - (nowUtcEpochSeconds - fiveMin));
        int secLeft15m = (int) (900 - (nowUtcEpochSeconds - fifteenMin));
        int secLeft1h = (int) (3600 - (nowUtcEpochSeconds - oneHour));

        // Cooldown from memory or last trade
        String cooldownInfo = tradingMemoryService.getCooldownInfo(); // implement this simple method

        formatted.put("marketData", String.format("""
            Timestamp (UTC): %s (all data age < 15s guaranteed)
            Current price: $%.2f | Bid: $%.5f | Ask: $%.5f
            Spread: $%.3f (%s bps) ← CRITICAL: >4 bps → HOLD
            Session VWAP (today): $%.2f
            24h Change: %+.2f%% | High: $%.2f | Low: $%.2f
            Current 5m volume vs 20-period avg: %.0f%%
            Candle timing → 5m: %ds left | 15m: %dm%02ds left | 1h: %dm%02ds left
            Cooldown: %s
            """,
                Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS),
                price, bid, ask,
                spreadUsd, spreadBps > 4 ? "WIDE → HOLD" : String.format("%.1f", spreadBps),
                sessionVwap,
                context.ticker.priceChangePercentAsDouble(),
                context.ticker.highPrice().doubleValue(),
                context.ticker.lowPrice().doubleValue(),
                current5mVolumeRatio * 100,
                secLeft5m,
                secLeft15m / 60, secLeft15m % 60,
                secLeft1h / 60, secLeft1h % 60,
                cooldownInfo
        ));

        // 2. TECHNICAL INDICATORS — now with ADX, DI, BB width, ATR, CCI, Williams %R
        formatted.put("technical5m", formatTechnicalsEnhanced(context.tech5m, "5m"));
        formatted.put("technical15m", formatTechnicalsEnhanced(context.tech15m, "15m"));
        formatted.put("technical1h", formatTechnicalsEnhanced(context.tech1h, "1h"));
        
        // 3. MULTI-TIMEFRAME ANALYSIS - Check for trend alignment
        formatted.put("multiTimeframeAnalysis", checkTrendAlignment(context.tech5m, context.tech15m, context.tech1h));

        // 4. KEY LEVELS - Pivot Points (from 1h or 15m context - using 1h as standard)
        formatted.put("keyLevels", formatKeyLevels(context.tech1h));

        // 5. SENTIMENT — kept but de-emphasized (tiebreaker only)
        formatted.put("sentiment", String.format("""
            Fear & Greed Index: %d (%s) → use as contrarian filter only
            Overall Sentiment: %s
            """,
                (int) getDoubleValue(context.sentiment, "fearGreedIndex"),
                getStringValue(context.sentiment, "fearGreedLabel"),
                getStringValue(context.sentiment, "classification")
        ));

        // 6. PORTFOLIO — exact USDC free balance
        double freeUsdc = context.portfolio.usdBalance().doubleValue(); // make sure this is FREE balance
        formatted.put("portfolio", String.format("""
            Free USDC: $%.2f (risk exactly 4%% per trade → max $%.2f risk)
            ETH: %.6f | Total equity: $%.2f
            """,
                freeUsdc,
                freeUsdc * 0.04,
                context.portfolio.ethBalance().doubleValue(),
                context.portfolio.totalValue().doubleValue()
        ));

        // 7. TRADING MEMORY — last 3 bullets only
        formatted.put("tradingMemory", context.tradingMemory);

        return formatted;
    }

    private String checkTrendAlignment(Map<String, Object> t5m, Map<String, Object> t15m, Map<String, Object> t1h) {
        double ema20_5m = getDoubleValue(t5m, "ema20");
        double ema50_5m = getDoubleValue(t5m, "ema50");
        double ema20_15m = getDoubleValue(t15m, "ema20");
        double ema50_15m = getDoubleValue(t15m, "ema50");
        double ema20_1h = getDoubleValue(t1h, "ema20");
        double ema50_1h = getDoubleValue(t1h, "ema50");
        
        boolean uptrend5m = ema20_5m > ema50_5m;
        boolean uptrend15m = ema20_15m > ema50_15m;
        boolean uptrend1h = ema20_1h > ema50_1h;
        
        String alignment;
        if (uptrend5m && uptrend15m && uptrend1h) alignment = "FULL BULLISH ALIGNMENT (Strong Uptrend)";
        else if (!uptrend5m && !uptrend15m && !uptrend1h) alignment = "FULL BEARISH ALIGNMENT (Strong Downtrend)";
        else if (uptrend5m && uptrend15m) alignment = "BULLISH INTRA-DAY (5m & 15m aligned)";
        else if (!uptrend5m && !uptrend15m) alignment = "BEARISH INTRA-DAY (5m & 15m aligned)";
        else alignment = "MIXED / CHOPPY (No clear alignment)";
        
        return String.format("""
            Trend Alignment (EMA20 vs EMA50):
            5m: %s | 15m: %s | 1h: %s
            Overall: %s
            """, 
            uptrend5m ? "UP" : "DOWN", 
            uptrend15m ? "UP" : "DOWN", 
            uptrend1h ? "UP" : "DOWN", 
            alignment
        );
    }
    
    private String formatKeyLevels(Map<String, Object> indicators) {
        @SuppressWarnings("unchecked")
        Map<String, Double> pivots = (Map<String, Double>) indicators.get("pivotPoints");
        
        if (pivots == null) return "Key Levels: N/A";
        
        double currentPrice = getDoubleValue(indicators, "price");
        
        return String.format("""
            Standard Pivot Points (1H based):
            R3: %s | R2: %s | R1: %s
            PIVOT: %s (Current Price: %.2f)
            S1: %s | S2: %s | S3: %s
            
            Immediate Resistance: %s
            Immediate Support: %s
            """,
            formatPivotLevel(pivots.get("r3")), formatPivotLevel(pivots.get("r2")), formatPivotLevel(pivots.get("r1")),
            formatPivotLevel(pivots.get("pivot")), currentPrice,
            formatPivotLevel(pivots.get("s1")), formatPivotLevel(pivots.get("s2")), formatPivotLevel(pivots.get("s3")),
            getImmediateLevel(currentPrice, pivots, true),
            getImmediateLevel(currentPrice, pivots, false)
        );
    }

    private String formatPivotLevel(Double level) {
        if (level == null || level < 0.01) return "N/A";
        return String.format("%.2f", level);
    }
    
    private String getImmediateLevel(double price, Map<String, Double> pivots, boolean resistance) {
        double closest = 0.0;
        double minDiff = Double.MAX_VALUE;
        String levelName = "None";
        
        for (Map.Entry<String, Double> entry : pivots.entrySet()) {
            double level = entry.getValue();
            if (level < 0.01) continue; // Skip zero/invalid levels

            if (resistance && level > price) {
                double diff = level - price;
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = level;
                    levelName = entry.getKey().toUpperCase();
                }
            } else if (!resistance && level < price) {
                double diff = price - level;
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = level;
                    levelName = entry.getKey().toUpperCase();
                }
            }
        }
        
        if (minDiff == Double.MAX_VALUE) return "None";
        return String.format("%s at %.2f (+%.2f%%)", levelName, closest, Math.abs(closest - price)/price * 100);
    }

    private String formatTechnicalsEnhanced(Map<String, Object> ind, String tf) {
        double adx = getDoubleValue(ind, "adx");
        double plusDi = getDoubleValue(ind, "plusDi");
        double minusDi = getDoubleValue(ind, "minusDi");
        double atr = getDoubleValue(ind, "atr");
        double bbWidth = getDoubleValue(ind, "bbWidthPct"); // (upper-lower)/middle *100
        double sessionVwap = binanceApiService.getSessionVwap();
        double cci = getDoubleValue(ind, "cci");
        double williamsR = getDoubleValue(ind, "williamsR");

        return String.format("""
            [%s] RSI: %.1f | MACD Hist: %+.4f
            ADX: %.1f | +DI: %.1f | -DI: %.1f → %s
            BB Width: %.2f%% | ATR: %.2f
            CCI: %.1f | Williams %%R: %.1f
            Price vs EMA20: %+.2f | vs VWAP: %+.2f
            """,
                tf,
                getDoubleValue(ind, "rsi"),
                getDoubleValue(ind, "macdHistogram"),
                adx, plusDi, minusDi,
                adx > 25 ? (plusDi > minusDi ? "STRONG UP" : "STRONG DOWN") : "WEAK/RANGING",
                bbWidth, atr,
                cci, williamsR,
                getDoubleValue(ind, "price") - getDoubleValue(ind, "ema20"),
                getDoubleValue(ind, "price") - sessionVwap
        );
    }

    // Helper methods
    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0.0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return 0.0;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "N/A";
    }

    /**
     * Container for all trading context data
     */
    public static class TradingContext {
        public net.pautet.softs.demospring.dto.Binance24hrTicker ticker;
        public Map<String, Object> tech5m;
        public Map<String, Object> tech15m;
        public Map<String, Object> tech1h;
        public Map<String, Object> sentiment;
        public AccountSummary portfolio;
        public String tradingMemory;
    }
}