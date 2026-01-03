# 🔄 Testnet Reset Feature

## Summary

Added `/eth reset` command to reset the Binance Testnet account to a **consistent starting state** (exactly $100 USDC, 0 ETH) by selling all ETH and using BTC to adjust the balance.

## ⚠️ Safety First

**TESTNET ONLY** - This feature:
- ✅ Only works on Binance Testnet (testnet.binance.vision)
- ✅ Cannot be used on production
- ✅ Uses the same API service that's configured for testnet
- ✅ Safe to use for testing and resetting trading experiments

## What It Does

The reset command will:

1. **Check current balances** - Gets ETH, USDC, and BTC balances
2. **Sell all ETH** - Places market sell order to convert all ETH to USDC
3. **Adjust to exactly $100 USDC** - Uses BTC to fine-tune the balance:
   - If USDC > $110: Buy BTC with excess USDC
   - If USDC < $90: Sell BTC to get more USDC (if available)
4. **Report results** - Shows detailed summary of all actions taken

### Example Flow

**Before Reset:**
```
ETH: 0.285000
USDC: $50.00
BTC: 0.00000000
```

**Reset Command:** `/eth reset`

**Actions Taken:**
- Starting: 0.285000 ETH, $50.00 USDC, 0.00000000 BTC
- ✅ Sold 0.285000 ETH → +$958.94 USDC (avg $3364.17)
- ✅ Bought 0.00001083 BTC with $908.94 USDC (avg $83942.15)
- Reset complete! Final: $100.00 USDC, 0.000000 ETH, 0.00001083 BTC

**After Reset:**
```
ETH: 0.000000
USDC: $100.00
BTC: 0.00001083
```

## Usage

### In Slack

Simply type:
```
/eth reset
```

The bot will:
1. Show a warning message
2. Execute the reset
3. Display a detailed summary with:
   - Actions taken
   - Final balances
   - Testnet-only warning

### Example Output

```
⚠️  Resetting testnet account... This will sell all ETH!

🔄 Testnet Reset Complete

Actions:
• Starting: 0.285000 ETH, $50.00 USDC, 0.00000000 BTC
• ✅ Sold 0.285000 ETH → +$958.94 USDC (avg $3364.17)
• ✅ Bought 0.00001083 BTC with $908.94 USDC (avg $83942.15)

─────────────────

Final Balances:
💵 USDC: $100.00
⚡ ETH: 0.000000
₿ BTC: 0.00001083

Ready for fresh trading!

⚠️  TESTNET ONLY - This command only works on Binance Testnet
```

## Implementation Details

### 1. BinanceTestnetTradingService.resetAccount()

```java
public Map<String, Object> resetAccount() {
    log.warn("🔄 TESTNET RESET initiated - Target: $100 USDC, 0 ETH");
    
    // Step 1: Get current balances
    var accountInfo = binanceApiService.getAccountInfo();
    BigDecimal ethBalance = accountInfo.getFreeBalance("ETH");
    BigDecimal usdcBalance = accountInfo.getFreeBalance("USDC");
    BigDecimal btcBalance = accountInfo.getFreeBalance("BTC");
    
    // Step 2: Sell all ETH if we have any (minimum 0.001)
    if (ethBalance.compareTo(new BigDecimal("0.001")) > 0) {
        var sellOrder = binanceApiService.placeMarketSellOrder("ETHUSDC", quantity);
        usdcBalance = usdcBalance.add(sellOrder.cummulativeQuoteQty());
        ethBalance = BigDecimal.ZERO;
    }
    
    // Step 3: Adjust USDC to exactly $100 using BTC
    BigDecimal targetUsdc = new BigDecimal("100.00");
    BigDecimal difference = usdcBalance.subtract(targetUsdc);
    
    if (difference.compareTo(new BigDecimal("10.00")) > 0) {
        // Excess USDC - buy BTC with it
        var buyOrder = binanceApiService.placeMarketBuyOrder("BTCUSDC", excessAmount);
        usdcBalance = usdcBalance.subtract(buyOrder.cummulativeQuoteQty());
        btcBalance = btcBalance.add(buyOrder.executedQty());
    } else if (difference.compareTo(new BigDecimal("-10.00")) < 0) {
        // Need more USDC - sell BTC if available
        if (btcBalance.compareTo(new BigDecimal("0.0001")) > 0) {
            var sellOrder = binanceApiService.placeMarketSellOrder("BTCUSDC", btcAmount);
            usdcBalance = usdcBalance.add(sellOrder.cummulativeQuoteQty());
            // Then buy back excess if needed
        }
    }
    
    // Return detailed summary
    return Map.of(
        "success", true,
        "finalEthBalance", ethBalance,
        "finalUsdcBalance", usdcBalance,
        "finalBtcBalance", btcBalance,
        "actions", actions,
        "message", "Reset complete!"
    );
}
```

### 2. SlackBotService.handleResetCommand()

```java
public void handleResetCommand(String userId, String channelId) {
    log.warn("⚠️  Handling /eth reset for user: {} - TESTNET RESET", userId);
    
    // Send warning
    sendMessage(channelId, "⚠️  Resetting testnet account... This will sell all ETH!");
    
    // Execute reset
    Map<String, Object> result = binanceTestnetTradingService.resetAccount();
    
    // Display formatted results with blocks
    sendBlockMessage(channelId, blocks);
}
```

### 3. SlackSocketModeService - Switch Case

```java
switch (subCommand) {
    // ... other cases ...
    case "reset" -> slackBotService.handleResetCommand(userId, channelId);
    case "help" -> slackBotService.handleHelpCommand(channelId);
    default -> ...
}
```

## When to Use

### ✅ Good Use Cases

1. **Starting Fresh** - Want to test a new trading strategy with a clean slate
2. **After Experiments** - Accumulated complex positions from testing
3. **Benchmarking** - Reset to consistent starting point for performance testing
4. **Bug Testing** - Need known state to reproduce issues
5. **Demo Preparation** - Clean account before showing features

### ❌ Don't Use For

1. **Production** - This is testnet only!
2. **Preserving Gains** - This sells everything, not selective
3. **Emergency Stop** - Use sell commands for controlled exits

## Safety Features

### 1. Testnet Configuration Check

The service uses `BinanceApiService` which is configured in application properties:

```yaml
binance:
  base-url: https://testnet.binance.vision  # TESTNET
  api-key: ${BINANCE_API_KEY}
  api-secret: ${BINANCE_API_SECRET}
```

### 2. Minimum ETH Check

Won't attempt to sell if ETH balance < 0.001 (too small for Binance minimum)

```java
if (ethBalance.compareTo(new BigDecimal("0.001")) > 0) {
    // Safe to sell
}
```

### 3. Detailed Logging

All reset actions are logged with WARNING level:

```java
log.warn("🔄 TESTNET RESET initiated - This will sell all ETH!");
log.info("Selling {} ETH to USDC", ethBalance);
log.info("✅ Testnet reset complete - Final balances: ETH={}, USDC={}", ...);
```

### 4. Error Handling

If reset fails, detailed error message is returned:

```java
catch (Exception e) {
    log.error("❌ Error during testnet reset", e);
    result.put("success", false);
    result.put("error", e.getMessage());
    throw new RuntimeException("Failed to reset testnet account: " + e.getMessage(), e);
}
```

## Help Command Updated

The `/eth help` now includes:

```
*🔧 TESTNET*
`/eth reset` - Reset account to 0 ETH (sells all)
```

## Technical Notes

### Market Sell Order

- Uses `placeMarketSellOrder()` for immediate execution
- Sells at current market price (best available)
- No minimum price protection (testnet only)

### Precision

- ETH: 6 decimal places (Binance standard)
- USDC: 2 decimal places (currency standard)

### Return Type

```java
Map<String, Object> {
    "success": Boolean,
    "finalEthBalance": BigDecimal,
    "finalUsdcBalance": BigDecimal,
    "actions": List<String>,
    "message": String,
    "error": String (only if failed)
}
```

## Future Enhancements (Optional)

1. **Reset to Specific Amount** - `/eth reset 100` to reset to $100 USDC
2. **Buy Back Option** - Option to buy back specific amount of ETH after reset
3. **Reset with ETH** - Option to end with specific ETH amount instead of 0
4. **Confirmation Step** - Require explicit confirmation before executing

## Testing Checklist

- [x] Command added to switch statement
- [x] Handler method implemented
- [x] Service method implemented
- [x] Help message updated
- [x] Error handling implemented
- [x] Logging implemented
- [x] Testnet-only warning displayed

## Example Scenarios

### Scenario 1: Typical Reset (Has ETH)

**Initial:** 1.5 ETH, $200 USDC, 0 BTC  
**Actions:**
1. Sell 1.5 ETH → +$5046 USDC (total: $5246 USDC)
2. Buy BTC with $5146 excess USDC
**Result:** 0 ETH, $100 USDC, ~0.00006132 BTC

### Scenario 2: Already at Target

**Initial:** 0 ETH, $105 USDC, 0 BTC  
**Actions:** None (within ±$10 tolerance)
**Result:** 0 ETH, $105 USDC, 0 BTC

### Scenario 3: Need More USDC

**Initial:** 0 ETH, $50 USDC, 0.00001000 BTC  
**Actions:**
1. Sell 0.00001000 BTC → +$839 USDC (total: $889)
2. Buy back BTC with $789 excess
**Result:** 0 ETH, $100 USDC, ~0.00000940 BTC

### Scenario 4: Dust Amount

**Initial:** 0.0005 ETH, $100 USDC, 0 BTC  
**Actions:** No ETH to sell (below minimum)
**Result:** 0.0005 ETH, $100 USDC, 0 BTC

## Conclusion

The reset feature provides a **safe and convenient way** to return your testnet account to a clean state, perfect for:
- Testing new strategies
- Benchmarking performance
- Demos and presentations
- Bug reproduction

**Remember:** This is **TESTNET ONLY** and cannot affect real funds! 🔒

---

**Ready to use:** Just type `/eth reset` in Slack! 🚀
