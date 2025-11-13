package net.pautet.softs.demospring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Binance Account Information Response
 * Endpoint: GET /api/v3/account
 * 
 * Returns account information including balances, trading permissions, and rate limits.
 * 
 * Official docs: https://binance-docs.github.io/apidocs/spot/en/#account-information-user_data
 */
public record BinanceAccountInfo(
    @JsonProperty("makerCommission")
    Integer makerCommission,
    
    @JsonProperty("takerCommission")
    Integer takerCommission,
    
    @JsonProperty("buyerCommission")
    Integer buyerCommission,
    
    @JsonProperty("sellerCommission")
    Integer sellerCommission,
    
    @JsonProperty("canTrade")
    Boolean canTrade,
    
    @JsonProperty("canWithdraw")
    Boolean canWithdraw,
    
    @JsonProperty("canDeposit")
    Boolean canDeposit,
    
    @JsonProperty("brokered")
    Boolean brokered,
    
    @JsonProperty("requireSelfTradePrevention")
    Boolean requireSelfTradePrevention,
    
    @JsonProperty("preventSor")
    Boolean preventSor,
    
    @JsonProperty("updateTime")
    Long updateTime,
    
    @JsonProperty("accountType")
    String accountType,
    
    @JsonProperty("balances")
    List<BinanceAccountBalance> balances,
    
    @JsonProperty("permissions")
    List<String> permissions
) {
    /**
     * Get balance for a specific asset
     */
    public Optional<BinanceAccountBalance> getBalance(String asset) {
        return balances.stream()
                .filter(b -> asset.equals(b.asset()))
                .findFirst();
    }
    
    /**
     * Get free balance for a specific asset
     */
    public BigDecimal getFreeBalance(String asset) {
        return getBalance(asset)
                .map(BinanceAccountBalance::free)
                .orElse(BigDecimal.ZERO);
    }
    
    /**
     * Get locked balance for a specific asset
     */
    public BigDecimal getLockedBalance(String asset) {
        return getBalance(asset)
                .map(BinanceAccountBalance::locked)
                .orElse(BigDecimal.ZERO);
    }
    
    /**
     * Get total balance for a specific asset (free + locked)
     */
    public BigDecimal getTotalBalance(String asset) {
        return getBalance(asset)
                .map(BinanceAccountBalance::total)
                .orElse(BigDecimal.ZERO);
    }
    
    /**
     * Get all balances that have non-zero amounts
     */
    public List<BinanceAccountBalance> getNonZeroBalances() {
        return balances.stream()
                .filter(b -> b.hasFreeBalance() || b.hasLockedBalance())
                .toList();
    }
    
    /**
     * Check if account can trade
     */
    public boolean isTradingEnabled() {
        return Boolean.TRUE.equals(canTrade);
    }
    
    /**
     * Check if account has SPOT trading permission
     */
    public boolean hasSpotPermission() {
        return permissions != null && permissions.contains("SPOT");
    }
    
    /**
     * Get maker commission as percentage
     */
    public double getMakerCommissionPercent() {
        return makerCommission / 10000.0; // Binance returns in basis points
    }
    
    /**
     * Get taker commission as percentage
     */
    public double getTakerCommissionPercent() {
        return takerCommission / 10000.0; // Binance returns in basis points
    }
}
