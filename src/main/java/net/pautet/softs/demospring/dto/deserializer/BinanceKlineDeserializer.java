package net.pautet.softs.demospring.dto.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import net.pautet.softs.demospring.dto.BinanceKline;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Custom deserializer for Binance Kline data
 * 
 * Binance returns klines as arrays instead of objects:
 * [openTime, open, high, low, close, volume, closeTime, ...]
 * 
 * This deserializer converts the array to a BinanceKline record.
 */
public class BinanceKlineDeserializer extends JsonDeserializer<BinanceKline> {
    
    @Override
    public BinanceKline deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        
        // Binance returns klines as arrays with 12 elements
        if (!node.isArray() || node.size() < 12) {
            throw new IOException("Invalid kline format: expected array with 12 elements");
        }
        
        return new BinanceKline(
            node.get(0).asLong(),                      // openTime
            new BigDecimal(node.get(1).asText()),      // open
            new BigDecimal(node.get(2).asText()),      // high
            new BigDecimal(node.get(3).asText()),      // low
            new BigDecimal(node.get(4).asText()),      // close
            new BigDecimal(node.get(5).asText()),      // volume
            node.get(6).asLong(),                      // closeTime
            new BigDecimal(node.get(7).asText()),      // quoteVolume
            node.get(8).asInt(),                       // trades
            new BigDecimal(node.get(9).asText()),      // takerBuyBaseVolume
            new BigDecimal(node.get(10).asText()),     // takerBuyQuoteVolume
            node.get(11).asText()                      // ignore
        );
    }
}
