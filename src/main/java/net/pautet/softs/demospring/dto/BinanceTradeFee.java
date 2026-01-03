package net.pautet.softs.demospring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Trade fee for a specific symbol
 * Endpoint: GET /sapi/v1/asset/tradeFee (USER_DATA)
 */
public record BinanceTradeFee(
        @JsonProperty("symbol") String symbol,
        @JsonProperty("makerCommission") BigDecimal makerCommission,
        @JsonProperty("takerCommission") BigDecimal takerCommission
) {}
