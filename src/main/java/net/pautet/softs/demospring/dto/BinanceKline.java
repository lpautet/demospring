package net.pautet.softs.demospring.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import net.pautet.softs.demospring.dto.deserializer.BinanceKlineDeserializer;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Binance Kline/Candlestick Response
 * Endpoint: GET /api/v3/klines
 * 
 * Binance returns klines as arrays: [openTime, open, high, low, close, volume, closeTime, ...]
 * This record provides a type-safe representation.
 * 
 * Official docs: https://binance-docs.github.io/apidocs/spot/en/#kline-candlestick-data
 */
@JsonDeserialize(using = BinanceKlineDeserializer.class)
public record BinanceKline(
    Long openTime,           // 0: Kline open time (milliseconds)
    BigDecimal open,         // 1: Open price
    BigDecimal high,         // 2: High price
    BigDecimal low,          // 3: Low price
    BigDecimal close,        // 4: Close price
    BigDecimal volume,       // 5: Volume
    Long closeTime,          // 6: Kline close time (milliseconds)
    BigDecimal quoteVolume,  // 7: Quote asset volume
    Integer trades,          // 8: Number of trades
    BigDecimal takerBuyBaseVolume,  // 9: Taker buy base asset volume
    BigDecimal takerBuyQuoteVolume, // 10: Taker buy quote asset volume
    String ignore            // 11: Unused field (always "0")
) {
    /**
     * Get open time as Instant
     */
    public Instant openTimeInstant() {
        return Instant.ofEpochMilli(openTime);
    }
    
    /**
     * Get close time as Instant
     */
    public Instant closeTimeInstant() {
        return Instant.ofEpochMilli(closeTime);
    }
    
    /**
     * Get open price as double for calculations
     */
    public double openAsDouble() {
        return open.doubleValue();
    }
    
    /**
     * Get high price as double
     */
    public double highAsDouble() {
        return high.doubleValue();
    }
    
    /**
     * Get low price as double
     */
    public double lowAsDouble() {
        return low.doubleValue();
    }
    
    /**
     * Get close price as double
     */
    public double closeAsDouble() {
        return close.doubleValue();
    }
    
    /**
     * Get volume as double
     */
    public double volumeAsDouble() {
        return volume.doubleValue();
    }
    
    /**
     * Check if this candle is bullish (close > open)
     */
    public boolean isBullish() {
        return close.compareTo(open) > 0;
    }
    
    /**
     * Check if this candle is bearish (close < open)
     */
    public boolean isBearish() {
        return close.compareTo(open) < 0;
    }
    
    /**
     * Get price change for this candle
     */
    public BigDecimal priceChange() {
        return close.subtract(open);
    }
    
    /**
     * Get price change percentage
     */
    public double priceChangePercent() {
        if (open.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return priceChange().divide(open, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
    
    /**
     * Get candle body size (high - low)
     */
    public BigDecimal range() {
        return high.subtract(low);
    }
}
