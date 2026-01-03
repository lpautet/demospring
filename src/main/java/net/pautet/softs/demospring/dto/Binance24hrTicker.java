package net.pautet.softs.demospring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Binance 24hr Ticker Statistics Response
 * Endpoint: GET /api/v3/ticker/24hr
 * 
 * 24 hour rolling window price change statistics.
 */
public record Binance24hrTicker(
    @JsonProperty("symbol")
    String symbol,
    
    @JsonProperty("priceChange")
    BigDecimal priceChange,
    
    @JsonProperty("priceChangePercent")
    BigDecimal priceChangePercent,
    
    @JsonProperty("weightedAvgPrice")
    BigDecimal weightedAvgPrice,
    
    @JsonProperty("prevClosePrice")
    BigDecimal prevClosePrice,
    
    @JsonProperty("lastPrice")
    BigDecimal lastPrice,
    
    @JsonProperty("lastQty")
    BigDecimal lastQty,
    
    @JsonProperty("bidPrice")
    BigDecimal bidPrice,
    
    @JsonProperty("bidQty")
    BigDecimal bidQty,
    
    @JsonProperty("askPrice")
    BigDecimal askPrice,
    
    @JsonProperty("askQty")
    BigDecimal askQty,
    
    @JsonProperty("openPrice")
    BigDecimal openPrice,
    
    @JsonProperty("highPrice")
    BigDecimal highPrice,
    
    @JsonProperty("lowPrice")
    BigDecimal lowPrice,
    
    @JsonProperty("volume")
    BigDecimal volume,
    
    @JsonProperty("quoteVolume")
    BigDecimal quoteVolume,
    
    @JsonProperty("openTime")
    Long openTime,
    
    @JsonProperty("closeTime")
    Long closeTime,
    
    @JsonProperty("firstId")
    Long firstId,
    
    @JsonProperty("lastId")
    Long lastId,
    
    @JsonProperty("count")
    Integer count
) {
    /**
     * Get price change as percentage (double)
     */
    public double priceChangePercentAsDouble() {
        return priceChangePercent.doubleValue();
    }
    
    /**
     * Get last price as double
     */
    public double lastPriceAsDouble() {
        return lastPrice.doubleValue();
    }
    
    /**
     * Get volume as double
     */
    public double volumeAsDouble() {
        return volume.doubleValue();
    }
    
    /**
     * Check if price is up
     */
    public boolean isPriceUp() {
        return priceChange.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Get bid price as double
     */
    public double bidPriceAsDouble() {
        return bidPrice.doubleValue();
    }
    
    /**
     * Get ask price as double
     */
    public double askPriceAsDouble() {
        return askPrice.doubleValue();
    }
}
