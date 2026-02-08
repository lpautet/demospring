package net.pautet.softs.demospring.service;

import net.pautet.softs.demospring.dto.AccountSummary;
import net.pautet.softs.demospring.dto.Binance24hrTicker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class TradingContextServiceTest {

    @Mock
    private BinanceApiService binanceApiService;

    @Mock
    private TechnicalIndicatorService technicalIndicatorService;

    @Mock
    private SentimentAnalysisService sentimentAnalysisService;

    @Mock
    private BinanceTradingService tradingService;

    @Mock
    private TradingMemoryService tradingMemoryService;

    private TradingContextService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TradingContextService(
                binanceApiService,
                technicalIndicatorService,
                sentimentAnalysisService,
                tradingService,
                tradingMemoryService
        );
    }

    @Test
    void formatForPrompt_shouldIncludeNewSections() {
        // Arrange
        // Mock Ticker - match the record constructor exactly
        Binance24hrTicker ticker = new Binance24hrTicker(
                "ETHUSDC",                      // symbol
                BigDecimal.valueOf(1.5),        // priceChange
                BigDecimal.valueOf(2.0),        // priceChangePercent
                BigDecimal.valueOf(2000.0),     // weightedAvgPrice
                BigDecimal.valueOf(2490.0),     // prevClosePrice
                BigDecimal.valueOf(2500.0),     // lastPrice
                BigDecimal.valueOf(0.5),        // lastQty
                BigDecimal.valueOf(2499.0),     // bidPrice
                BigDecimal.valueOf(10.0),       // bidQty
                BigDecimal.valueOf(2501.0),     // askPrice
                BigDecimal.valueOf(15.0),       // askQty
                BigDecimal.valueOf(2495.0),     // openPrice
                BigDecimal.valueOf(2550.0),     // highPrice
                BigDecimal.valueOf(2450.0),     // lowPrice
                BigDecimal.valueOf(1000.0),     // volume
                BigDecimal.valueOf(2500000.0),  // quoteVolume
                1000L,                          // openTime
                2000L,                          // closeTime
                100L,                           // firstId
                200L,                           // lastId
                500                             // count
        );
        when(binanceApiService.get24hrTicker(anyString())).thenReturn(ticker);
        when(binanceApiService.getSessionVwap()).thenReturn(2480.0);

        // Mock Indicators
        Map<String, Object> indicators = new HashMap<>();
        indicators.put("price", 2500.0);
        indicators.put("rsi", 60.0);
        indicators.put("adx", 30.0);
        indicators.put("plusDi", 35.0);
        indicators.put("minusDi", 20.0);
        indicators.put("ema20", 2480.0);
        indicators.put("ema50", 2450.0);
        indicators.put("cci", 120.0);
        indicators.put("williamsR", -15.0);
        
        Map<String, Double> pivots = new HashMap<>();
        pivots.put("pivot", 2500.0);
        pivots.put("r1", 2550.0);
        pivots.put("s1", 2450.0);
        indicators.put("pivotPoints", pivots);

        when(technicalIndicatorService.calculateIndicators(anyString(), anyString(), anyInt())).thenReturn(indicators);
        when(technicalIndicatorService.getVolumeRatio5m(anyString())).thenReturn(1.2);

        // Mock Sentiment
        Map<String, Object> sentiment = new HashMap<>();
        sentiment.put("fearGreedIndex", 60.0);
        sentiment.put("fearGreedLabel", "Greed");
        sentiment.put("classification", "Bullish");
        when(sentimentAnalysisService.getMarketSentiment(anyString())).thenReturn(sentiment);

        // Mock Portfolio - match the record constructor exactly
        AccountSummary account = new AccountSummary(
                BigDecimal.valueOf(1000.0), // usdcBalance
                BigDecimal.valueOf(1.0),    // ethBalance
                BigDecimal.valueOf(2500.0), // ethPrice
                BigDecimal.valueOf(2500.0), // ethValue
                BigDecimal.valueOf(3500.0), // totalValue
                5,                          // totalTrades
                BigDecimal.ZERO,            // usdcLocked
                BigDecimal.valueOf(1000.0), // usdcTotal
                BigDecimal.ZERO,            // ethLocked
                BigDecimal.valueOf(1.0),    // ethTotal
                BigDecimal.valueOf(3500.0), // totalValueFree
                BigDecimal.valueOf(3500.0)  // totalValueTotal
        );
        when(tradingService.getAccountSummary()).thenReturn(account);

        // Mock Memory
        when(tradingMemoryService.getTradingMemoryContext()).thenReturn("Test Memory");
        when(tradingMemoryService.getCooldownInfo()).thenReturn("No cooldown");

        // Act
        Map<String, String> result = service.formatForPrompt();

        // Assert
        assertTrue(result.containsKey("keyLevels"), "Should contain keyLevels");
        assertTrue(result.containsKey("multiTimeframeAnalysis"), "Should contain multiTimeframeAnalysis");
        
        String technical5m = result.get("technical5m");
        assertTrue(technical5m.contains("CCI"), "Technical 5m should contain CCI");
        assertTrue(technical5m.contains("Williams %R"), "Technical 5m should contain Williams %R");
        
        String keyLevels = result.get("keyLevels");
        assertTrue(keyLevels.contains("Pivot Points"), "Key levels should mention Pivot Points");
        assertTrue(keyLevels.contains("Immediate Resistance"), "Key levels should mention Immediate Resistance");
    }
}