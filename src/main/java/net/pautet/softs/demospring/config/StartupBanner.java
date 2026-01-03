package net.pautet.softs.demospring.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Displays a prominent startup banner showing the current trading mode
 * This ensures operators know exactly what mode the bot is running in
 */
@Slf4j
@Component
public class StartupBanner {

    private final TradingModeConfig tradingModeConfig;

    public StartupBanner(TradingModeConfig tradingModeConfig) {
        this.tradingModeConfig = tradingModeConfig;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void displayBanner() {
        String mode = tradingModeConfig.getTradingMode().toUpperCase();
        boolean isProduction = tradingModeConfig.isProduction();
        String baseUrl = tradingModeConfig.getBaseUrl();
        boolean hasCredentials = tradingModeConfig.getApiKey() != null 
                && !tradingModeConfig.getApiKey().isEmpty();

        // Clear separator
        log.info("");
        log.info("═══════════════════════════════════════════════════════════════════");
        
        if (isProduction) {
            // PRODUCTION MODE - Big red warning
            log.warn("╔═══════════════════════════════════════════════════════════════╗");
            log.warn("║                                                               ║");
            log.warn("║              ⚠️  PRODUCTION MODE - REAL MONEY! ⚠️              ║");
            log.warn("║                                                               ║");
            log.warn("║  All trades will execute on the LIVE Binance exchange!       ║");
            log.warn("║  Real money is at risk!                                      ║");
            log.warn("║                                                               ║");
            log.warn("╚═══════════════════════════════════════════════════════════════╝");
            log.warn("");
            log.warn("  Trading Mode: {}", mode);
            log.warn("  Binance URL:  {}", baseUrl);
            log.warn("  Credentials:  {}", hasCredentials ? "✓ CONFIGURED" : "✗ MISSING");
            log.warn("");
        } else {
            // TESTNET MODE - Safe green banner
            log.info("╔═══════════════════════════════════════════════════════════════╗");
            log.info("║                                                               ║");
            log.info("║              ✅ TESTNET MODE - SAFE TESTING ✅                 ║");
            log.info("║                                                               ║");
            log.info("║  Using Binance Testnet with fake money                       ║");
            log.info("║  Safe for development and testing                            ║");
            log.info("║                                                               ║");
            log.info("╚═══════════════════════════════════════════════════════════════╝");
            log.info("");
            log.info("  Trading Mode: {}", mode);
            log.info("  Binance URL:  {}", baseUrl);
            log.info("  Credentials:  {}", hasCredentials ? "✓ CONFIGURED" : "✗ MISSING");
            log.info("");
        }
        
        log.info("═══════════════════════════════════════════════════════════════════");
        log.info("");
        
        // Additional warnings if credentials are missing
        if (!hasCredentials) {
            log.error("❌ ERROR: No API credentials configured for {} mode!", mode);
            log.error("   Please set the following in your .env file:");
            if (isProduction) {
                log.error("   - BINANCE_PRODUCTION_API_KEY");
                log.error("   - BINANCE_PRODUCTION_API_SECRET");
            } else {
                log.error("   - BINANCE_TESTNET_API_KEY");
                log.error("   - BINANCE_TESTNET_API_SECRET");
            }
            log.error("");
        }
    }
}
