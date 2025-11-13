package net.pautet.softs.demospring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Minimal OCO order response mapping for Binance Spot API
 * Endpoint: POST /api/v3/order/oco
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceOcoOrderResponse(
    @JsonProperty("orderListId")
    Long orderListId,

    @JsonProperty("contingencyType")
    String contingencyType,

    @JsonProperty("listStatusType")
    String listStatusType,

    @JsonProperty("listOrderStatus")
    String listOrderStatus,

    @JsonProperty("symbol")
    String symbol,

    @JsonProperty("orders")
    List<OcoOrder> orders
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OcoOrder(
        @JsonProperty("symbol")
        String symbol,
        @JsonProperty("orderId")
        Long orderId,
        @JsonProperty("clientOrderId")
        String clientOrderId
    ) {}
}
