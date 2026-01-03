package net.pautet.softs.demospring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Binance Order Response
 * Returned when placing orders (market, limit, etc.)
 * 
 * Official docs: https://binance-docs.github.io/apidocs/spot/en/#new-order-trade
 */
public record BinanceOrderResponse(
    @JsonProperty("symbol")
    String symbol,
    
    @JsonProperty("orderId")
    Long orderId,
    
    @JsonProperty("orderListId")
    Long orderListId,
    
    @JsonProperty("clientOrderId")
    String clientOrderId,
    
    @JsonProperty("transactTime")
    Long transactTime,
    
    @JsonProperty("price")
    BigDecimal price,

    @JsonProperty("stopPrice")
    BigDecimal stopPrice,
    
    @JsonProperty("origQty")
    BigDecimal origQty,
    
    @JsonProperty("executedQty")
    BigDecimal executedQty,
    
    @JsonProperty("cummulativeQuoteQty")
    BigDecimal cummulativeQuoteQty,
    
    @JsonProperty("status")
    String status,
    
    @JsonProperty("timeInForce")
    String timeInForce,
    
    @JsonProperty("type")
    String type,
    
    @JsonProperty("side")
    String side,
    
    @JsonProperty("workingTime")
    Long workingTime,

    @JsonProperty("time")
    Long time,

    @JsonProperty("updateTime")
    Long updateTime,
    
    @JsonProperty("fills")
    List<Fill> fills,
    
    @JsonProperty("selfTradePreventionMode")
    String selfTradePreventionMode
) {
    /**
     * Fill details for the order
     */
    public record Fill(
        @JsonProperty("price")
        BigDecimal price,
        
        @JsonProperty("qty")
        BigDecimal qty,
        
        @JsonProperty("commission")
        BigDecimal commission,
        
        @JsonProperty("commissionAsset")
        String commissionAsset,
        
        @JsonProperty("tradeId")
        Long tradeId
    ) {
        public double priceAsDouble() {
            return price.doubleValue();
        }
        
        public double qtyAsDouble() {
            return qty.doubleValue();
        }
    }
    
    /**
     * Check if order was filled
     */
    public boolean isFilled() {
        return "FILLED".equals(status);
    }
    
    /**
     * Check if order is pending
     */
    public boolean isPending() {
        return "NEW".equals(status) || "PARTIALLY_FILLED".equals(status);
    }
    
    /**
     * Check if order was cancelled
     */
    public boolean isCancelled() {
        return "CANCELED".equals(status) || "EXPIRED".equals(status) || "REJECTED".equals(status);
    }
    
    /**
     * Check if this was a buy order
     */
    public boolean isBuyOrder() {
        return "BUY".equals(side);
    }
    
    /**
     * Check if this was a sell order
     */
    public boolean isSellOrder() {
        return "SELL".equals(side);
    }
    
    /**
     * Get average execution price
     */
    public BigDecimal getAveragePrice() {
        if (executedQty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return cummulativeQuoteQty.divide(executedQty, 8, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * Get total commission paid
     */
    public BigDecimal getTotalCommission() {
        if (fills == null || fills.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return fills.stream()
                .map(Fill::commission)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Get executed quantity as double
     */
    public double executedQtyAsDouble() {
        return executedQty.doubleValue();
    }
    
    /**
     * Get cumulative quote quantity as double
     */
    public double cummulativeQuoteQtyAsDouble() {
        return cummulativeQuoteQty.doubleValue();
    }
}
