package net.pautet.softs.demospring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Binance Price Ticker Response
 * Endpoint: GET /api/v3/ticker/price
 * 
 * Simple price ticker for a symbol.
 */
public record BinanceTickerPrice(
    @JsonProperty("symbol")
    String symbol,
    
    @JsonProperty("price")
    BigDecimal price
) {
    /**
     * Get price as double for convenience
     */
    public double priceAsDouble() {
        return price.doubleValue();
    }
}
