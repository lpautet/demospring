package net.pautet.softs.demospring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Binance Trade Response
 * Endpoint: GET /api/v3/myTrades
 * 
 * Represents an individual trade execution.
 * 
 * Official docs: https://binance-docs.github.io/apidocs/spot/en/#account-trade-list-user_data
 */
public record BinanceTrade(
    @JsonProperty("symbol")
    String symbol,
    
    @JsonProperty("id")
    Long id,
    
    @JsonProperty("orderId")
    Long orderId,
    
    @JsonProperty("orderListId")
    Long orderListId,
    
    @JsonProperty("price")
    BigDecimal price,
    
    @JsonProperty("qty")
    BigDecimal qty,
    
    @JsonProperty("quoteQty")
    BigDecimal quoteQty,
    
    @JsonProperty("commission")
    BigDecimal commission,
    
    @JsonProperty("commissionAsset")
    String commissionAsset,
    
    @JsonProperty("time")
    Long time,
    
    @JsonProperty("isBuyer")
    Boolean isBuyer,
    
    @JsonProperty("isMaker")
    Boolean isMaker,
    
    @JsonProperty("isBestMatch")
    Boolean isBestMatch
) {
    /**
     * Get trade time as Instant
     */
    public Instant timeInstant() {
        return Instant.ofEpochMilli(time);
    }
    
    /**
     * Get side (BUY or SELL) based on isBuyer
     */
    public String side() {
        return Boolean.TRUE.equals(isBuyer) ? "BUY" : "SELL";
    }
    
    /**
     * Check if this was a buy trade
     */
    public boolean isBuyTrade() {
        return Boolean.TRUE.equals(isBuyer);
    }
    
    /**
     * Check if this was a sell trade
     */
    public boolean isSellTrade() {
        return !Boolean.TRUE.equals(isBuyer);
    }
    
    /**
     * Check if this was a maker trade (limit order)
     */
    public boolean isMakerTrade() {
        return Boolean.TRUE.equals(isMaker);
    }
    
    /**
     * Check if this was a taker trade (market order)
     */
    public boolean isTakerTrade() {
        return !Boolean.TRUE.equals(isMaker);
    }
    
    /**
     * Get price as double
     */
    public double priceAsDouble() {
        return price.doubleValue();
    }
    
    /**
     * Get quantity as double
     */
    public double qtyAsDouble() {
        return qty.doubleValue();
    }
    
    /**
     * Get quote quantity as double
     */
    public double quoteQtyAsDouble() {
        return quoteQty.doubleValue();
    }
    
    /**
     * Get commission as double
     */
    public double commissionAsDouble() {
        return commission.doubleValue();
    }
    
    /**
     * Get commission percentage relative to quote quantity
     */
    public double commissionPercent() {
        if (quoteQty.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        // Commission is usually in the asset being bought/sold
        // Calculate as percentage of trade value
        return commission.divide(quoteQty, 6, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
