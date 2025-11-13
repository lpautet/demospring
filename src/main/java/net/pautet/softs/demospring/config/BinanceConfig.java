package net.pautet.softs.demospring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "binance")
public class BinanceConfig {
    private String apiKey;
    private String apiSecret;
    
    /**
     * Testnet mode flag
     * - true (default): Use testnet, lenient data requirements (min 3 candles)
     * - false: Production mode, strict data requirements (min 50 candles)
     * 
     * Set in application.properties: binance.testnet=true/false
     */
    private boolean testnet = true; // Default to testnet for safety
}
