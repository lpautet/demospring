package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.AccountSummary;
import org.springframework.stereotype.Service;

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

        // Market Data
        double price = context.ticker.lastPriceAsDouble();
        double priceChange = context.ticker.priceChange().doubleValue();
        double priceChangePercent = context.ticker.priceChangePercentAsDouble();
        double volume = context.ticker.volumeAsDouble();
        String changeSign = priceChange >= 0 ? "+" : "";

        formatted.put("marketData", String.format("""
                Price: $%.2f
                24h Change: %s%.2f%%%% ($%s%.2f)
                24h High: $%.2f
                24h Low: $%.2f
                24h Volume: %.0f ETH
                """,
                price,
                changeSign, priceChangePercent, changeSign, priceChange,
                context.ticker.highPrice().doubleValue(),
                context.ticker.lowPrice().doubleValue(),
                volume
        ));

        // Technical Indicators
        formatted.put("technical5m", formatTechnicals(context.tech5m));
        formatted.put("technical15m", formatTechnicals(context.tech15m));
        formatted.put("technical1h", formatTechnicals(context.tech1h));

        // Sentiment
        double overallScore = getDoubleValue(context.sentiment, "overallScore");
        String classification = getStringValue(context.sentiment, "classification");
        int fearGreedIndex = (int) getDoubleValue(context.sentiment, "fearGreedIndex");
        String fearGreedLabel = getStringValue(context.sentiment, "fearGreedLabel");
        String interpretation = getStringValue(context.sentiment, "interpretation");

        formatted.put("sentiment", String.format("""
                Overall Score: %.2f
                Classification: %s
                Fear & Greed Index: %d (%s)
                Interpretation: %s
                """,
                overallScore,
                classification,
                fearGreedIndex,
                fearGreedLabel,
                interpretation
        ));

        // Portfolio
        formatted.put("portfolio", String.format("""
                Account Type: BINANCE TESTNET (Real execution, fake money)
                USDC Balance: $%.2f
                ETH Balance: %.6f ETH ($%.2f)
                Total Value: $%.2f
                Total Trades: %d
                """,
                context.portfolio.usdBalance().doubleValue(),
                context.portfolio.ethBalance().doubleValue(),
                context.portfolio.ethValue().doubleValue(),
                context.portfolio.totalValue().doubleValue(),
                context.portfolio.totalTrades()
        ));

        // Trading Memory
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
}
