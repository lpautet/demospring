package net.pautet.softs.demospring.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.AccountSummary;
import net.pautet.softs.demospring.dto.BinanceOrderResponse;
import net.pautet.softs.demospring.dto.BinanceTrade;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Provides AI function calling tools for ETH trading operations.
 * These functions allow the AI to access market data and execute testnet trades.
 */
@Slf4j
@Service
public class TradingFunctions {

    private final BinanceApiService binanceApiService;
    private final BinanceTradingService tradingService;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final SentimentAnalysisService sentimentAnalysisService;
    private final ObjectMapper objectMapper;

    public TradingFunctions(BinanceApiService binanceApiService, 
                           BinanceTradingService tradingService,
                           TechnicalIndicatorService technicalIndicatorService,
                           SentimentAnalysisService sentimentAnalysisService,
                           ObjectMapper objectMapper) {
        this.binanceApiService = binanceApiService;
        this.tradingService = tradingService;
        this.technicalIndicatorService = technicalIndicatorService;
        this.sentimentAnalysisService = sentimentAnalysisService;
        this.objectMapper = objectMapper;
    }

    /**
     * Request object for getting market data
     */
    @JsonClassDescription("Request to get current ETH market data and statistics")
    public record GetMarketDataRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The username to analyze for")
            String username
    ) {}

    /**
     * Request object for portfolio information
     */
    @JsonClassDescription("Request to get user's paper trading portfolio")
    public record GetPortfolioRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The username to get portfolio for")
            String username
    ) {}

    /**
     * Request object for executing testnet trades
     */
    @JsonClassDescription("Request to execute a testnet trade (real Binance testnet execution)")
    public record ExecuteTradeRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The username executing the trade")
            String username,
            
            @JsonProperty(required = true)
            @JsonPropertyDescription("Trade action: BUY or SELL")
            String action,
            
            @JsonProperty(required = true)
            @JsonPropertyDescription("Amount: USD for BUY, ETH quantity for SELL")
            String amount,
            
            @JsonProperty(required = false)
            @JsonPropertyDescription("Reason or strategy for this trade")
            String reason
    ) {}

    /**
     * Request object for technical indicators
     */
    @JsonClassDescription("Request to get technical indicators for algorithmic analysis")
    public record GetTechnicalIndicatorsRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The username requesting analysis")
            String username,
            
            @JsonProperty(required = true)
            @JsonPropertyDescription("Timeframe: 5m, 15m, 1h, 4h, 1d")
            String timeframe
    ) {}

    /**
     * Request object for sentiment analysis
     */
    @JsonClassDescription("Request to get market sentiment analysis")
    public record GetSentimentAnalysisRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The username requesting sentiment")
            String username,
            
            @JsonProperty(required = true)
            @JsonPropertyDescription("Symbol to analyze (e.g., ETHUSDC)")
            String symbol
    ) {}

    /**
     * Get current ETH market data
     * Returns comprehensive market information including price, 24h change, volume, etc.
     */
    @Bean
    @Description("Get current ETH/USDC market data from Binance. Returns current price, 24h price change, high/low, volume, and other market statistics. Use this to analyze market conditions before making trading decisions.")
    public Function<GetMarketDataRequest, String> getMarketData() {
        return request -> {
            try {
                log.info("AI calling getMarketData for user: {}", request.username());
                
                // Get 24h ticker data (includes price, change, volume, etc.)
                var ticker24h = binanceApiService.get24hrTicker(BinanceApiService.ETHUSDC);
                
                Map<String, Object> marketData = new HashMap<>();
                marketData.put("symbol", ticker24h.symbol());
                marketData.put("currentPrice", ticker24h.lastPrice().toPlainString());
                marketData.put("priceChange24h", ticker24h.priceChange().toPlainString());
                marketData.put("priceChangePercent24h", ticker24h.priceChangePercent().toPlainString());
                marketData.put("highPrice24h", ticker24h.highPrice().toPlainString());
                marketData.put("lowPrice24h", ticker24h.lowPrice().toPlainString());
                marketData.put("volume24h", ticker24h.volume().toPlainString());
                marketData.put("quoteVolume24h", ticker24h.quoteVolume().toPlainString());
                marketData.put("openPrice", ticker24h.openPrice().toPlainString());
                
                String result = objectMapper.writeValueAsString(marketData);
                log.debug("getMarketData result: {}", result);
                return result;
            } catch (Exception e) {
                log.error("Error in getMarketData function: {}", e.getMessage(), e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        };
    }

    /**
     * Get user's testnet trading portfolio
     * Returns current balances and trading statistics
     */
    @Bean
    @Description("Get user's Binance testnet portfolio including USD balance, ETH holdings, and trading statistics. Use this to understand current positions before recommending trades.")
    public Function<GetPortfolioRequest, String> getPortfolio() {
        return request -> {
            try {
                log.info("AI calling getPortfolio for user: {}", request.username());
                
                AccountSummary summary = tradingService.getAccountSummary();
                BigDecimal currentPrice = tradingService.getCurrentPrice();
                
                Map<String, Object> portfolioData = new HashMap<>();
                portfolioData.put("username", request.username());
                portfolioData.put("usdBalance", summary.usdBalance().toString());
                portfolioData.put("ethBalance", summary.ethBalance().toString());
                portfolioData.put("currentEthPrice", currentPrice.toString());
                portfolioData.put("totalValue", summary.totalValue().toString());
                portfolioData.put("totalTrades", String.valueOf(summary.totalTrades()));
                portfolioData.put("mode", "TESTNET");
                
                String result = objectMapper.writeValueAsString(portfolioData);
                log.debug("getPortfolio result: {}", result);
                return result;
            } catch (Exception e) {
                log.error("Error in getPortfolio function: {}", e.getMessage(), e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        };
    }

    /**
     * Execute a testnet trade (BUY or SELL)
     * Real execution on Binance testnet with fake money
     */
    @Bean
    @Description("Execute a testnet trade on Binance testnet. Action must be BUY (using USD) or SELL (using ETH quantity). Amount is USD for BUY, ETH quantity for SELL. Returns trade confirmation with price, quantity, and updated balances. Use this ONLY when user explicitly requests a trade or confirms your recommendation.")
    public Function<ExecuteTradeRequest, String> executeTrade() {
        return request -> {
            try {
                log.info("AI calling executeTrade - user: {} action: {} amount: {}", 
                        request.username(), request.action(), request.amount());
                
                BigDecimal amount = new BigDecimal(request.amount());
                String action = request.action().toUpperCase();
                
                BinanceOrderResponse order;
                if ("BUY".equals(action)) {
                    order = tradingService.buyETH(amount);
                } else if ("SELL".equals(action)) {
                    order = tradingService.sellETH(amount);
                } else {
                    return "{\"error\": \"Invalid action. Must be BUY or SELL\"}";
                }
                
                // Get updated portfolio
                AccountSummary summary = tradingService.getAccountSummary();
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("orderId", order.orderId());
                result.put("action", action);
                result.put("executedQty", order.executedQty());
                result.put("avgPrice", order.getAveragePrice());
                result.put("status", order.status());
                result.put("newUsdBalance", summary.usdBalance().toString());
                result.put("newEthBalance", summary.ethBalance().toString());
                result.put("newTotalValue", summary.totalValue().toString());
                result.put("mode", "TESTNET");
                
                String response = objectMapper.writeValueAsString(result);
                log.info("executeTrade success: {}", response);
                return response;
            } catch (IllegalArgumentException e) {
                log.warn("Trade failed for {}: {}", request.username(), e.getMessage());
                return "{\"error\": \"" + e.getMessage() + "\"}";
            } catch (Exception e) {
                log.error("Error in executeTrade function: {}", e.getMessage(), e);
                return "{\"error\": \"Trade execution failed: " + e.getMessage() + "\"}";
            }
        };
    }

    /**
     * Get recent trade history
     */
    @Bean
    @Description("Get user's recent testnet trade history. Returns list of past trades with details including action, price, quantity, timestamp, and commission. Use this to understand trading patterns and performance.")
    public Function<GetPortfolioRequest, String> getTradeHistory() {
        return request -> {
            try {
                log.info("AI calling getTradeHistory for user: {}", request.username());
                
                List<BinanceTrade> trades = tradingService.getRecentTrades(10);
                
                String result = objectMapper.writeValueAsString(trades);
                log.debug("getTradeHistory returned {} trades", trades.size());
                return result;
            } catch (Exception e) {
                log.error("Error in getTradeHistory function: {}", e.getMessage(), e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        };
    }

    /**
     * Get technical indicators for algorithmic trading
     * Returns RSI, MACD, Bollinger Bands, Moving Averages, ATR, VWAP, Stochastic
     */
    @Bean
    @Description("Get comprehensive technical indicators for algorithmic trading analysis. Returns RSI (momentum), MACD (trend), Bollinger Bands (volatility), Moving Averages (SMA20/50, EMA12/26), ATR (volatility %), VWAP (institutional levels), Stochastic Oscillator, and aggregated signal. Use this for multi-timeframe technical analysis to identify optimal entry/exit points. Timeframe options: 5m (scalping), 15m (intraday), 1h (swing), 4h (position), 1d (long-term).")
    public Function<GetTechnicalIndicatorsRequest, String> getTechnicalIndicators() {
        return request -> {
            try {
                log.info("AI calling getTechnicalIndicators - user: {} timeframe: {}", 
                        request.username(), request.timeframe());
                
                Map<String, Object> indicators = technicalIndicatorService.calculateIndicators(
                        "ETHUSDC", 
                        request.timeframe(), 
                        200
                );
                
                String result = objectMapper.writeValueAsString(indicators);
                log.debug("getTechnicalIndicators result for {}: {}", request.timeframe(), result);
                return result;
            } catch (Exception e) {
                log.error("Error in getTechnicalIndicators function: {}", e.getMessage(), e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        };
    }

    /**
     * Get market sentiment analysis
     * Returns Fear & Greed Index, news sentiment, momentum sentiment
     */
    @Bean
    @Description("Get comprehensive market sentiment analysis combining Fear & Greed Index (contrarian indicator), news sentiment, and market momentum. Returns overall sentiment score (-1.0 to +1.0), classification (VERY_BULLISH to VERY_BEARISH), and detailed breakdown of each component. Use this to gauge market psychology and combine with technical analysis for stronger trading signals. Extreme fear (score < -0.5) often indicates buying opportunities, extreme greed (score > 0.5) suggests caution.")
    public Function<GetSentimentAnalysisRequest, String> getSentimentAnalysis() {
        return request -> {
            try {
                log.info("AI calling getSentimentAnalysis - user: {} symbol: {}", 
                        request.username(), request.symbol());
                
                Map<String, Object> sentiment = sentimentAnalysisService.getMarketSentiment(request.symbol());
                
                String result = objectMapper.writeValueAsString(sentiment);
                log.debug("getSentimentAnalysis result: {}", result);
                return result;
            } catch (Exception e) {
                log.error("Error in getSentimentAnalysis function: {}", e.getMessage(), e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        };
    }
}
