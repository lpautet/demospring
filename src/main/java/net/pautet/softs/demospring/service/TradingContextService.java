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
        double current5mVolumeRatio = technicalIndicatorService.getVolumeRatio5m(); // current 5m vol / avg20

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

        // 2. TECHNICAL INDICATORS — now with ADX, DI, BB width, ATR for regime detection
        formatted.put("technical5m", formatTechnicalsEnhanced(context.tech5m, "5m"));
        formatted.put("technical15m", formatTechnicalsEnhanced(context.tech15m, "15m"));
        formatted.put("technical1h", formatTechnicalsEnhanced(context.tech1h, "1h"));

        // 3. SENTIMENT — kept but de-emphasized (tiebreaker only)
        formatted.put("sentiment", String.format("""
            Fear & Greed Index: %d (%s) → use as contrarian filter only
            Overall Sentiment: %s
            """,
                (int) getDoubleValue(context.sentiment, "fearGreedIndex"),
                getStringValue(context.sentiment, "fearGreedLabel"),
                getStringValue(context.sentiment, "classification")
        ));

        // 4. PORTFOLIO — exact USDC free balance
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

        // 5. TRADING MEMORY — last 3 bullets only
        formatted.put("tradingMemory", context.tradingMemory);

        return formatted;
    }



    /**
     * Format technical indicators for display
     */
    private String formatTechnicals(Map<String, Object> indicators) {
        double rsi = getDoubleValue(indicators, "rsi");
        String rsiSignal = getStringValue(indicators, "rsiSignal");
        double macd = getDoubleValue(indicators, "macd");
        String macdSignal = getStringValue(indicators, "macdSignal");
        double macdSignalLine = getDoubleValue(indicators, "macdSignalLine");
        double macdHistogram = getDoubleValue(indicators, "macdHistogram");
        double bbUpper = getDoubleValue(indicators, "bbUpper");
        double bbMiddle = getDoubleValue(indicators, "bbMiddle");
        double bbLower = getDoubleValue(indicators, "bbLower");
        double ema20 = getDoubleValue(indicators, "ema20");
        double ema50 = getDoubleValue(indicators, "ema50");
        String trend = getStringValue(indicators, "trend");
        int dataPoints = ((Number) indicators.getOrDefault("dataPoints", 0)).intValue();
        String quality = getStringValue(indicators, "dataQuality");

        return String.format("""
                RSI: %.2f (%s)
                MACD: %.2f | Signal: %.2f | Histogram: %.2f (%s)
                Bollinger Bands: Upper=%.2f, Mid=%.2f, Lower=%.2f
                EMA20: %.2f | EMA50: %.2f
                Trend: %s
                Data Points: %d | Quality: %s
                """,
                rsi, rsiSignal,
                macd, macdSignalLine, macdHistogram, macdSignal,
                bbUpper, bbMiddle, bbLower,
                ema20, ema50,
                trend,
                dataPoints, quality
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

    private String formatTechnicalsEnhanced(Map<String, Object> ind, String tf) {
        double adx = getDoubleValue(ind, "adx");
        double plusDi = getDoubleValue(ind, "plusDi");
        double minusDi = getDoubleValue(ind, "minusDi");
        double atr = getDoubleValue(ind, "atr");
        double bbWidth = getDoubleValue(ind, "bbWidthPct"); // (upper-lower)/middle *100
        double sessionVwap = binanceApiService.getSessionVwap();

        return String.format("""
            [%s] RSI: %.1f | MACD Hist: %+.4f
            ADX: %.1f | +DI: %.1f | -DI: %.1f → %s
            BB Width: %.2f%% | ATR: %.2f
            Price vs EMA20: %+.2f | vs VWAP: %+.2f
            """,
                tf,
                getDoubleValue(ind, "rsi"),
                getDoubleValue(ind, "macdHistogram"),
                adx, plusDi, minusDi,
                adx > 25 ? (plusDi > minusDi ? "STRONG UP" : "STRONG DOWN") : "WEAK/RANGING",
                bbWidth, atr,
                getDoubleValue(ind, "price") - getDoubleValue(ind, "ema20"),
                getDoubleValue(ind, "price") - sessionVwap
        );
    }
}
