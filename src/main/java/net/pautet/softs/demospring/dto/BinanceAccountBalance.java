package net.pautet.softs.demospring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Binance Account Balance
 * Represents a single asset balance in the account
 */
public record BinanceAccountBalance(
    @JsonProperty("asset")
    String asset,
    
    @JsonProperty("free")
    BigDecimal free,
    
    @JsonProperty("locked")
    BigDecimal locked
) {
    /**
     * Get total balance (free + locked)
     */
    public BigDecimal total() {
        return free.add(locked);
    }
    
    /**
     * Get free balance as double
     */
    public double freeAsDouble() {
        return free.doubleValue();
    }
    
    /**
     * Get locked balance as double
     */
    public double lockedAsDouble() {
        return locked.doubleValue();
    }
    
    /**
     * Get total balance as double
     */
    public double totalAsDouble() {
        return total().doubleValue();
    }
    
    /**
     * Check if this asset has any free balance
     */
    public boolean hasFreeBalance() {
        return free.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if this asset has any locked balance
     */
    public boolean hasLockedBalance() {
        return locked.compareTo(BigDecimal.ZERO) > 0;
    }
}
