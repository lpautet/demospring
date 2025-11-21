package net.pautet.softs.demospring.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration for trading mode (testnet vs production)
 * Controls which Binance API credentials and endpoints to use
 */
@Slf4j
@Getter
@Configuration
public class TradingModeConfig {

    @Value("${trading.mode:testnet}")
    private String tradingMode;

    @Value("${binance.testnet.api-key:}")
    private String testnetApiKey;

    @Value("${binance.testnet.api-secret:}")
    private String testnetApiSecret;

    @Value("${binance.production.api-key:}")
    private String productionApiKey;

    @Value("${binance.production.api-secret:}")
    private String productionApiSecret;

    /**
     * Check if running in testnet mode
     */
    public boolean isTestnet() {
        return "testnet".equalsIgnoreCase(tradingMode);
    }

    /**
     * Check if running in production mode
     */
    public boolean isProduction() {
        return "production".equalsIgnoreCase(tradingMode);
    }

    /**
     * Get the appropriate API key based on current mode
     */
    public String getApiKey() {
        return isTestnet() ? testnetApiKey : productionApiKey;
    }

    /**
     * Get the appropriate API secret based on current mode
     */
    public String getApiSecret() {
        return isTestnet() ? testnetApiSecret : productionApiSecret;
    }

    /**
     * Get the base URL for Binance API based on mode
     */
    public String getBaseUrl() {
        return isTestnet() 
            ? "https://testnet.binance.vision" 
            : "https://api.binance.com";
    }

    @PostConstruct
    public void validateConfiguration() {
        // Just validate, don't log (StartupBanner handles logging)
        if (getApiKey() == null || getApiKey().isEmpty()) {
            log.debug("No API key configured for {} mode", tradingMode);
        }
    }
}
