package net.pautet.softs.demospring.service;

import net.pautet.softs.demospring.config.BinanceConfig;
import net.pautet.softs.demospring.dto.BinanceKline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class TechnicalIndicatorServiceTest {

    @Mock
    private BinanceApiService binanceApiService;

    @Mock
    private BinanceConfig binanceConfig;

    private TechnicalIndicatorService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TechnicalIndicatorService(binanceApiService, binanceConfig);
    }

    @Test
    void calculateIndicators_shouldIncludeNewIndicators() {
        // Arrange
        when(binanceConfig.isTestnet()).thenReturn(false);
        List<BinanceKline> klines = generateDummyKlines(100);
        when(binanceApiService.getKlines(anyString(), anyString(), anyInt())).thenReturn(klines);

        // Act
        Map<String, Object> result = service.calculateIndicators("ETHUSDC", "5m", 100);

        // Assert
        assertNotNull(result);
        assertFalse(result.containsKey("error"));
        
        // Check new indicators
        assertTrue(result.containsKey("cci"), "Should contain CCI");
        assertTrue(result.containsKey("cciSignal"), "Should contain CCI Signal");
        assertTrue(result.containsKey("williamsR"), "Should contain Williams %R");
        assertTrue(result.containsKey("williamsRSignal"), "Should contain Williams %R Signal");
        assertTrue(result.containsKey("pivotPoints"), "Should contain Pivot Points");
        
        // Check types
        assertTrue(result.get("cci") instanceof Double);
        assertTrue(result.get("williamsR") instanceof Double);
        assertTrue(result.get("pivotPoints") instanceof Map);
        
        @SuppressWarnings("unchecked")
        Map<String, Double> pivots = (Map<String, Double>) result.get("pivotPoints");
        assertTrue(pivots.containsKey("pivot"));
        assertTrue(pivots.containsKey("r1"));
        assertTrue(pivots.containsKey("s1"));
    }

    private List<BinanceKline> generateDummyKlines(int count) {
        List<BinanceKline> klines = new ArrayList<>();
        double price = 1000.0;
        for (int i = 0; i < count; i++) {
            price += (Math.random() - 0.5) * 10;
            BigDecimal p = BigDecimal.valueOf(price);
            // High is p + 10, Low is p - 10, Open = p, Close = p
            klines.add(new BinanceKline(
                    System.currentTimeMillis() - (count - i) * 60000L,
                    p, 
                    p.add(BigDecimal.TEN), 
                    p.subtract(BigDecimal.TEN), 
                    p,
                    BigDecimal.valueOf(100),
                    System.currentTimeMillis(),
                    BigDecimal.valueOf(100000), 10, BigDecimal.ZERO, BigDecimal.ZERO, "0"
            ));
        }
        return klines;
    }
}
