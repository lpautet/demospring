package net.pautet.softs.demospring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Optional;

/**
 * Binance Exchange Information Response
 * Endpoint: GET /api/v3/exchangeInfo
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceExchangeInfo(
    String timezone,
    Long serverTime,
    List<BinanceSymbolInfo> symbols
) {
    
    /**
     * Find symbol info by symbol name
     * @param symbol Trading pair (e.g., "ETHUSDC")
     * @return Symbol info if found
     */
    public Optional<BinanceSymbolInfo> findSymbol(String symbol) {
        return symbols.stream()
            .filter(s -> symbol.equals(s.symbol()))
            .findFirst();
    }
}
