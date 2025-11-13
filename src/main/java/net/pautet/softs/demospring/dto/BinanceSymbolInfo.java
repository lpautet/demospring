package net.pautet.softs.demospring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Binance Symbol Information from /api/v3/exchangeInfo
 * Contains trading rules and filters for a specific trading pair
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceSymbolInfo(
    String symbol,
    String status,
    
    @JsonProperty("baseAsset")
    String baseAsset,  // e.g., "ETH"
    
    @JsonProperty("quoteAsset")
    String quoteAsset, // e.g., "USDC"
    
    @JsonProperty("baseAssetPrecision")
    int baseAssetPrecision,
    
    @JsonProperty("quoteAssetPrecision")
    int quoteAssetPrecision,
    
    List<SymbolFilter> filters
) {
    
    /**
     * Get LOT_SIZE filter (quantity constraints)
     */
    public Optional<LotSizeFilter> getLotSizeFilter() {
        return filters.stream()
            .filter(f -> "LOT_SIZE".equals(f.filterType()))
            .findFirst()
            .map(f -> new LotSizeFilter(
                new BigDecimal(f.minQty()),
                new BigDecimal(f.maxQty()),
                new BigDecimal(f.stepSize())
            ));
    }
    
    /**
     * Get MIN_NOTIONAL filter (minimum order value)
     */
    public Optional<BigDecimal> getMinNotional() {
        return filters.stream()
            .filter(f -> "MIN_NOTIONAL".equals(f.filterType()) || "NOTIONAL".equals(f.filterType()))
            .findFirst()
            .map(f -> new BigDecimal(f.minNotional() != null ? f.minNotional() : "0"));
    }
    
    /**
     * Get PRICE_FILTER (price constraints)
     */
    public Optional<PriceFilter> getPriceFilter() {
        return filters.stream()
            .filter(f -> "PRICE_FILTER".equals(f.filterType()))
            .findFirst()
            .map(f -> new PriceFilter(
                new BigDecimal(f.minPrice()),
                new BigDecimal(f.maxPrice()),
                new BigDecimal(f.tickSize())
            ));
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SymbolFilter(
        String filterType,
        String minQty,
        String maxQty,
        String stepSize,
        String minPrice,
        String maxPrice,
        String tickSize,
        String minNotional
    ) {}
    
    public record LotSizeFilter(
        BigDecimal minQty,
        BigDecimal maxQty,
        BigDecimal stepSize
    ) {
        /**
         * Get the number of decimal places allowed for quantity
         */
        public int getQuantityPrecision() {
            String stepStr = stepSize.stripTrailingZeros().toPlainString();
            int dotIndex = stepStr.indexOf('.');
            if (dotIndex == -1) return 0;
            return stepStr.length() - dotIndex - 1;
        }
    }
    
    public record PriceFilter(
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal tickSize
    ) {
        /**
         * Get the number of decimal places allowed for price
         */
        public int getPricePrecision() {
            String tickStr = tickSize.stripTrailingZeros().toPlainString();
            int dotIndex = tickStr.indexOf('.');
            if (dotIndex == -1) return 0;
            return tickStr.length() - dotIndex - 1;
        }
    }
}
