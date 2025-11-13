package net.pautet.softs.demospring.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.AccountSummary;
import net.pautet.softs.demospring.dto.BinanceOrderResponse;
import net.pautet.softs.demospring.dto.BinanceTrade;
import net.pautet.softs.demospring.dto.BinanceOcoOrderResponse;
import net.pautet.softs.demospring.exception.BinanceApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.pautet.softs.demospring.service.BinanceApiService.ETHUSDC;

/**
 * Binance Trading Service - Testnet Only
 * <p>
 * Uses Binance Testnet for realistic trading simulation with fake money.
 * Enabled when binance.testnet=true and API credentials are configured.
 * <p>
 * Benefits over paper trading:
 * - Real order book and market conditions
 * - Actual order execution and slippage
 * - Test strategies in realistic environment
 * - No risk with real money
 * <p>
 * Get testnet credentials:
 * https://testnet.binance.vision/
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "binance.testnet", havingValue = "true")
public class BinanceTradingService {

    public static final String SYMBOL_ETHUSDC = ETHUSDC;
    public static final String SYMBOL_BTCUSDC = "BTCUSDC";
    
    // Trading rules - loaded dynamically from Binance API
    private int quantityScale = 5; // Default fallback
    private BigDecimal minQuantity = new BigDecimal("0.00001"); // Default fallback
    private BigDecimal minNotional = new BigDecimal("5.00"); // Default fallback
    private int priceScale = 2; // Default fallback (updated from PRICE_FILTER)
    
    @org.springframework.beans.factory.annotation.Value("${trading.oco.stop-limit-offset:0.005}")
    private java.math.BigDecimal ocoStopLimitOffset;

    private final BinanceApiService binanceApiService;

    public BinanceTradingService(BinanceApiService binanceApiService) {
        this.binanceApiService = binanceApiService;
    }
    
    /**
     * Load trading rules from Binance on startup
     */
    @PostConstruct
    public void loadTradingRules() {
        try {
            log.debug("Loading trading rules for {} from Binance...", SYMBOL_ETHUSDC);
            
            var exchangeInfo = binanceApiService.getExchangeInfo(SYMBOL_ETHUSDC);
            var symbolInfo = exchangeInfo.findSymbol(SYMBOL_ETHUSDC)
                .orElseThrow(() -> new BinanceApiException("Symbol " + SYMBOL_ETHUSDC + " not found in exchange info"));
            
            // Load LOT_SIZE filter
            symbolInfo.getLotSizeFilter().ifPresentOrElse(
                lotSize -> {
                    this.quantityScale = lotSize.getQuantityPrecision();
                    this.minQuantity = lotSize.minQty();
                    log.debug("✅ LOT_SIZE: minQty={}, stepSize={}, precision={} decimals",
                        lotSize.minQty(), lotSize.stepSize(), quantityScale);
                },
                () -> log.warn("⚠️  LOT_SIZE filter not found, using defaults")
            );
            
            // Load MIN_NOTIONAL filter
            symbolInfo.getMinNotional().ifPresentOrElse(
                notional -> {
                    this.minNotional = notional;
                    log.debug("✅ MIN_NOTIONAL: ${}", notional);
                },
                () -> log.warn("⚠️  MIN_NOTIONAL filter not found, using default $5")
            );
            
            // Load PRICE_FILTER
            symbolInfo.getPriceFilter().ifPresentOrElse(
                priceFilter -> {
                    this.priceScale = priceFilter.getPricePrecision();
                    log.debug("✅ PRICE_FILTER: tickSize precision={} decimals", priceScale);
                },
                () -> log.warn("⚠️  PRICE_FILTER not found, using default {} decimals", priceScale)
            );
            
            log.debug("✅ Trading rules loaded successfully for {}", SYMBOL_ETHUSDC);
            
        } catch (Exception e) {
            log.error("❌ Failed to load trading rules from Binance, using hardcoded defaults", e);
            // Keep default fallback values
        }
    }

    /**
     * Place a LIMIT BUY using a USDC amount and a target limit price.
     * Converts USDC to ETH quantity using limit price and exchange precision.
     */
    public BinanceOrderResponse buyETHLimitUSD(BigDecimal usdcAmount, BigDecimal limitPrice) {
        try {
            if (usdcAmount.compareTo(minNotional) < 0) {
                throw new IllegalArgumentException(
                    String.format("Order amount $%.2f is below minimum $%.2f", usdcAmount, minNotional));
            }

            // Compute quantity = USDC / price
            BigDecimal qty = usdcAmount.divide(limitPrice, quantityScale + 2, RoundingMode.DOWN)
                    .setScale(quantityScale, RoundingMode.DOWN);

            if (qty.compareTo(minQuantity) < 0) {
                throw new IllegalArgumentException(
                    String.format("Computed quantity %s ETH is below minimum %s ETH", qty, minQuantity));
            }

            String priceStr = fmtPrice(limitPrice);
            String qtyStr = fmtQty(qty);

            log.info("Placing LIMIT BUY: {} USDC @ ${} (qty {} ETH)", usdcAmount, priceStr, qtyStr);
            return binanceApiService.placeLimitOrder(SYMBOL_ETHUSDC, "BUY", qtyStr, priceStr, "GTC");
        } catch (Exception e) {
            log.error("Error placing limit buy order", e);
            throw new BinanceApiException("Failed to place limit buy order: " + e.getMessage(), e);
        }
    }

    /**
     * Place a LIMIT SELL with ETH quantity and target price.
     */
    public BinanceOrderResponse sellETHLimit(BigDecimal ethAmount, BigDecimal limitPrice) {
        try {
            BigDecimal qty = ethAmount.setScale(quantityScale, RoundingMode.DOWN);
            if (qty.compareTo(minQuantity) < 0) {
                throw new IllegalArgumentException(
                    String.format("Order quantity %s ETH is below minimum %s ETH", qty.toPlainString(), minQuantity.toPlainString()));
            }

            String priceStr = fmtPrice(limitPrice);
            String qtyStr = fmtQty(qty);

            log.info("Placing LIMIT SELL: {} ETH @ ${}", qtyStr, priceStr);
            return binanceApiService.placeLimitOrder(SYMBOL_ETHUSDC, "SELL", qtyStr, priceStr, "GTC");
        } catch (Exception e) {
            log.error("Error placing limit sell order", e);
            throw new BinanceApiException("Failed to place limit sell order: " + e.getMessage(), e);
        }
    }

    /**
     * Place an OCO exit (take-profit limit + stop-limit) after a BUY fill.
     * Quantity should be the filled base quantity (ETH).
     */
    public BinanceOcoOrderResponse placeOcoSellExit(BigDecimal quantity, BigDecimal takeProfit, BigDecimal stopLoss) {
        try {
            BigDecimal qty = quantity.setScale(quantityScale, RoundingMode.DOWN);
            if (qty.compareTo(minQuantity) < 0) {
                throw new IllegalArgumentException(
                    String.format("OCO quantity %s ETH is below minimum %s ETH", qty, minQuantity));
            }

            // Stop-limit price slightly below stop trigger to ensure placement (configurable)
            BigDecimal stopLimit = stopLoss.multiply(BigDecimal.ONE.subtract(
                    ocoStopLimitOffset != null ? ocoStopLimitOffset : new BigDecimal("0.005")));

            String qtyStr = fmtQty(qty);
            String tpStr = fmtPrice(takeProfit);
            String stopStr = fmtPrice(stopLoss);
            String stopLimitStr = fmtPrice(stopLimit);

            log.info("Placing OCO SELL: qty {} | TP ${} | SL ${}/${}", qtyStr, tpStr, stopStr, stopLimitStr);
            return binanceApiService.placeOcoSellOrder(
                    SYMBOL_ETHUSDC, qtyStr, tpStr, stopStr, stopLimitStr, "GTC");
        } catch (Exception e) {
            log.error("Error placing OCO exit", e);
            throw new BinanceApiException("Failed to place OCO exit: " + e.getMessage(), e);
        }
    }

    // Helpers
    private String fmtQty(BigDecimal quantity) {
        return quantity.setScale(quantityScale, RoundingMode.DOWN).toPlainString();
    }

    private String fmtPrice(BigDecimal price) {
        return price.setScale(priceScale, RoundingMode.DOWN).toPlainString();
    }

    /**
     * Check if testnet API is properly configured
     */
    public boolean isConfigured() {
        try {
            binanceApiService.getAccountInfo();
            return true;
        } catch (Exception e) {
            log.warn("Binance testnet not properly configured: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get account summary with balances and stats
     */
    public AccountSummary getAccountSummary() {
        try {
            var accountInfo = binanceApiService.getAccountInfo();
            
            // Free balances
            BigDecimal usdcFree = accountInfo.getFreeBalance("USDC");
            BigDecimal ethFree = accountInfo.getFreeBalance("ETH");

            // Locked balances
            BigDecimal usdcLocked = accountInfo.getLockedBalance("USDC");
            BigDecimal ethLocked = accountInfo.getLockedBalance("ETH");

            // Totals (free + locked)
            BigDecimal usdcTotal = usdcFree.add(usdcLocked);
            BigDecimal ethTotal = ethFree.add(ethLocked);

            // Get current ETH price
            BigDecimal ethPrice = binanceApiService.getPrice(ETHUSDC).price();

            // Valuations
            BigDecimal ethValueFree = ethFree.multiply(ethPrice);
            BigDecimal ethValueTotal = ethTotal.multiply(ethPrice);
            BigDecimal totalValueFree = usdcFree.add(ethValueFree);
            BigDecimal totalValueTotal = usdcTotal.add(ethValueTotal);

            // Get trade count for stats
            int tradeCount = 0;
            try {
                var trades = getRecentTrades(50);
                tradeCount = trades.size();
            } catch (Exception e) {
                log.warn("Could not fetch trade count", e);
            }

            return new AccountSummary(
                usdcFree,
                ethFree,
                ethPrice,
                ethValueFree,
                totalValueFree,
                tradeCount,
                usdcLocked,
                usdcTotal,
                ethLocked,
                ethTotal,
                totalValueFree,
                totalValueTotal
            );

        } catch (Exception e) {
            log.error("Error getting account summary", e);
            throw new BinanceApiException("Failed to get account summary", e);
        }
    }

    /**
     * Buy ETH on Binance Testnet
     * @param usdcAmount Amount in USDC to spend
     * @return Order response
     */
    public BinanceOrderResponse buyETH(BigDecimal usdcAmount) {
        try {
            // Validate minimum notional
            if (usdcAmount.compareTo(minNotional) < 0) {
                throw new IllegalArgumentException(
                    String.format("Order amount $%.2f is below minimum $%.2f", 
                        usdcAmount, minNotional));
            }
            
            log.info("Placing BUY order for {} USDC on testnet", usdcAmount);

            // Format to 2 decimal places for USDC
            String quoteOrderQty = usdcAmount.setScale(2, RoundingMode.DOWN).toPlainString();

            var order = binanceApiService.placeMarketBuyOrder(SYMBOL_ETHUSDC, quoteOrderQty);

            log.info("BUY order executed: {} ETH at ${}", order.executedQty(), order.getAveragePrice());

            return order;

        } catch (Exception e) {
            log.error("Error placing buy order", e);
            throw new BinanceApiException("Failed to place buy order: " + e.getMessage(), e);
        }
    }

    /**
     * Sell ETH on Binance Testnet
     * @param ethAmount Amount of ETH to sell
     * @return Order response
     */
    public BinanceOrderResponse sellETH(BigDecimal ethAmount) {
        try {
            // Round to correct precision (dynamically loaded from Binance)
            BigDecimal adjustedAmount = ethAmount.setScale(quantityScale, RoundingMode.DOWN);
            
            // Validate minimum quantity
            if (adjustedAmount.compareTo(minQuantity) < 0) {
                throw new IllegalArgumentException(
                    String.format("Order quantity %s ETH is below minimum %s ETH", 
                        adjustedAmount.toPlainString(), minQuantity.toPlainString()));
            }
            
            log.info("Placing SELL order for {} ETH on testnet (adjusted to {} decimals)", 
                adjustedAmount, quantityScale);

            String quantity = adjustedAmount.toPlainString();

            var order = binanceApiService.placeMarketSellOrder(SYMBOL_ETHUSDC, quantity);

            log.info("SELL order executed: {} ETH at ${}", order.executedQty(), order.getAveragePrice());

            return order;

        } catch (Exception e) {
            log.error("Error placing sell order", e);
            throw new BinanceApiException("Failed to place sell order: " + e.getMessage(), e);
        }
    }

    /**
     * Get recent trades from Binance Testnet
     * @param limit Number of trades to retrieve (max 1000)
     * @return List of trades
     */
    public List<BinanceTrade> getRecentTrades(int limit) {
        try {
            return binanceApiService.getMyTrades(SYMBOL_ETHUSDC, Math.min(limit, 100));
        } catch (Exception e) {
            log.error("Error getting recent trades", e);
            throw new BinanceApiException("Failed to get recent trades", e);
        }
    }

    /**
     * Get current ETH price
     */
    public BigDecimal getCurrentPrice() {
        try {
            return binanceApiService.getPrice(ETHUSDC).price();
        } catch (Exception e) {
            log.error("Error getting current price", e);
            throw new BinanceApiException("Failed to get current price", e);
        }
    }
    
    /**
     * Validate and adjust ETH quantity to meet Binance trading rules
     * @param ethAmount Raw ETH amount
     * @return Adjusted amount that meets exchange requirements, or null if below minimum
     */
    public BigDecimal validateAndAdjustQuantity(BigDecimal ethAmount) {
        if (ethAmount == null) {
            return null;
        }
        
        // Adjust to correct precision (dynamically loaded)
        BigDecimal adjusted = ethAmount.setScale(quantityScale, RoundingMode.DOWN);
        
        // Check minimum
        if (adjusted.compareTo(minQuantity) < 0) {
            log.warn("Quantity {} ETH is below minimum {} ETH", adjusted, minQuantity);
            return null;
        }
        
        return adjusted;
    }

    /**
     * Reset testnet account to initial state (exactly $100 USDC, 0 ETH, rest in BTC)
     * TESTNET ONLY - sells all ETH, then adjusts to exactly $100 USDC using BTC
     * 
     * @return Summary of reset actions
     */
    public Map<String, Object> resetAccount() {
        log.warn("🔄 TESTNET RESET initiated - Target: $100 USDC, 0 ETH");
        Map<String, Object> result = new HashMap<>();
        List<String> actions = new ArrayList<>();
        
        try {
            // Get current balances
            var accountInfo = binanceApiService.getAccountInfo();
            BigDecimal ethBalance = accountInfo.getFreeBalance("ETH");
            BigDecimal usdcBalance = accountInfo.getFreeBalance("USDC");
            BigDecimal btcBalance = accountInfo.getFreeBalance("BTC");
            
            log.info("Current balances - ETH: {}, USDC: {}, BTC: {}", ethBalance, usdcBalance, btcBalance);
            actions.add(String.format("Starting: %.6f ETH, $%.2f USDC, %.8f BTC", 
                ethBalance.doubleValue(), usdcBalance.doubleValue(), btcBalance.doubleValue()));
            
            // Step 1: Sell all ETH if we have any
            if (ethBalance.compareTo(new BigDecimal("0.001")) > 0) {
                log.info("Step 1: Selling {} ETH to USDC", ethBalance);
                
                String quantity = ethBalance.setScale(6, RoundingMode.DOWN).toPlainString();
                var sellOrder = binanceApiService.placeMarketSellOrder(SYMBOL_ETHUSDC, quantity);
                
                BigDecimal receivedUsdc = sellOrder.cummulativeQuoteQty();
                actions.add(String.format("✅ Sold %.6f ETH → +$%.2f USDC (avg $%.2f)", 
                    sellOrder.executedQty().doubleValue(), 
                    receivedUsdc.doubleValue(),
                    sellOrder.getAveragePrice().doubleValue()));
                
                usdcBalance = usdcBalance.add(receivedUsdc);
            } else {
                actions.add("ℹ️  No ETH to sell");
            }
            
            // Step 2: Adjust USDC to exactly $100 using BTC
            BigDecimal targetUsdc = new BigDecimal("100.00");
            BigDecimal usdcDifference = usdcBalance.subtract(targetUsdc);
            
            log.info("Step 2: Current USDC: {}, Target: {}, Difference: {}", usdcBalance, targetUsdc, usdcDifference);
            
            if (usdcDifference.compareTo(new BigDecimal("10.00")) > 0) {
                // We have excess USDC - buy BTC with it
                log.info("Converting excess ${} USDC to BTC", usdcDifference);
                
                // Use quoteOrderQty to buy exactly the amount we want to spend
                String quoteAmount = usdcDifference.setScale(2, RoundingMode.DOWN).toPlainString();
                var buyOrder = binanceApiService.placeMarketBuyOrder(SYMBOL_BTCUSDC, quoteAmount);
                
                BigDecimal spentUsdc = buyOrder.cummulativeQuoteQty();
                BigDecimal receivedBtc = buyOrder.executedQty();
                
                actions.add(String.format("✅ Bought %.8f BTC with $%.2f USDC (avg $%.2f)", 
                    receivedBtc.doubleValue(),
                    spentUsdc.doubleValue(),
                    buyOrder.getAveragePrice().doubleValue()));
                
            } else if (usdcDifference.compareTo(new BigDecimal("-10.00")) < 0) {
                // We need more USDC - sell some BTC
                BigDecimal neededUsdc = usdcDifference.abs();
                log.info("Need ${} more USDC - checking BTC balance", neededUsdc);
                
                if (btcBalance.compareTo(new BigDecimal("0.0001")) > 0) {
                    // Calculate how much BTC to sell (estimate based on current price)
                    // We'll use a market sell and it will sell what's needed
                    String btcToSell = btcBalance.setScale(8, RoundingMode.DOWN).toPlainString();
                    var sellOrder = binanceApiService.placeMarketSellOrder(SYMBOL_BTCUSDC, btcToSell);
                    
                    BigDecimal receivedUsdc = sellOrder.cummulativeQuoteQty();
                    actions.add(String.format("✅ Sold %.8f BTC → +$%.2f USDC", 
                        sellOrder.executedQty().doubleValue(),
                        receivedUsdc.doubleValue()));
                    
                    usdcBalance = usdcBalance.add(receivedUsdc);

                    // If we now have excess, buy BTC again
                    BigDecimal newDifference = usdcBalance.subtract(targetUsdc);
                    if (newDifference.compareTo(new BigDecimal("10.00")) > 0) {
                        String excessAmount = newDifference.setScale(2, RoundingMode.DOWN).toPlainString();
                        var buyBackOrder = binanceApiService.placeMarketBuyOrder(SYMBOL_BTCUSDC, excessAmount);
                        
                        actions.add(String.format("✅ Bought back %.8f BTC with excess $%.2f", 
                            buyBackOrder.executedQty().doubleValue(),
                            buyBackOrder.cummulativeQuoteQty().doubleValue()));
                    }
                } else {
                    actions.add(String.format("⚠️  Need $%.2f more USDC but no BTC to sell", neededUsdc.doubleValue()));
                }
            } else {
                actions.add(String.format("ℹ️  USDC balance close to target ($%.2f)", usdcBalance.doubleValue()));
            }
            
            // Refresh final balances
            accountInfo = binanceApiService.getAccountInfo();
            ethBalance = accountInfo.getFreeBalance("ETH");
            usdcBalance = accountInfo.getFreeBalance("USDC");
            btcBalance = accountInfo.getFreeBalance("BTC");
            
            // Final balances
            result.put("success", true);
            result.put("finalEthBalance", ethBalance);
            result.put("finalUsdcBalance", usdcBalance);
            result.put("finalBtcBalance", btcBalance);
            result.put("actions", actions);
            result.put("message", String.format("Reset complete! Final: $%.2f USDC, %.6f ETH, %.8f BTC", 
                usdcBalance.doubleValue(), ethBalance.doubleValue(), btcBalance.doubleValue()));
            
            log.info("✅ Testnet reset complete - Final: USDC=${}, ETH={}, BTC={}", usdcBalance, ethBalance, btcBalance);
            
        } catch (Exception e) {
            log.error("❌ Error during testnet reset", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("actions", actions);
            throw new BinanceApiException("Failed to reset testnet account: " + e.getMessage(), e);
        }
        
        return result;
    }
}
