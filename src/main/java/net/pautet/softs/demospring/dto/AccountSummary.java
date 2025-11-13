package net.pautet.softs.demospring.dto;

import java.math.BigDecimal;

/**
 * Account summary with balances and trading stats
 */
public record AccountSummary(
    // Free balances (backward compatible names)
    BigDecimal usdcBalance,
    BigDecimal ethBalance,
    
    // Current price and valuation based on FREE balances
    BigDecimal ethPrice,
    BigDecimal ethValue,
    BigDecimal totalValue,
    
    int totalTrades,
    
    // New: locked and total balances
    BigDecimal usdcLocked,
    BigDecimal usdcTotal,
    BigDecimal ethLocked,
    BigDecimal ethTotal,
    
    // New: portfolio valuation (free vs total)
    BigDecimal totalValueFree,
    BigDecimal totalValueTotal
) {
    /**
     * Alias for usdcBalance (backward compatibility)
     */
    public BigDecimal usdBalance() {
        return usdcBalance;
    }
    
    /**
     * Deprecated alias for usdcBalance
     * @deprecated Use usdcBalance() instead
     */
    @Deprecated
    public BigDecimal usdtBalance() {
        return usdcBalance;
    }
}
