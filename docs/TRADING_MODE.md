# Trading Mode Configuration

## Overview

The trading bot supports two modes:
- **Testnet Mode** (default): Safe testing with fake money on Binance Testnet
- **Production Mode**: Real trading with real money on Binance

## Configuration

### Environment Variables

Set `TRADING_MODE` in your `.env` file:

```bash
# Safe testing (default)
TRADING_MODE=testnet

# Real money trading
TRADING_MODE=production
```

### Testnet Credentials

For testnet mode, set:

```bash
BINANCE_TESTNET_API_KEY=your-testnet-key
BINANCE_TESTNET_API_SECRET=your-testnet-secret
```

Get testnet API keys from: https://testnet.binance.vision/

### Production Credentials

⚠️ **WARNING: Production mode uses REAL MONEY!**

For production mode, set:

```bash
BINANCE_PRODUCTION_API_KEY=your-production-key
BINANCE_PRODUCTION_API_SECRET=your-production-secret
```

Get production API keys from: https://www.binance.com/en/my/settings/api-management

## How It Works

The `TradingModeConfig` class automatically:

1. **Selects the correct API endpoint:**
   - Testnet: `https://testnet.binance.vision`
   - Production: `https://api.binance.com`

2. **Uses the appropriate credentials:**
   - Testnet: `BINANCE_TESTNET_API_KEY` / `BINANCE_TESTNET_API_SECRET`
   - Production: `BINANCE_PRODUCTION_API_KEY` / `BINANCE_PRODUCTION_API_SECRET`

3. **Logs the current mode on startup:**
   ```
   ╔════════════════════════════════════════════════════════════╗
   ║  TRADING MODE: TESTNET (SAFE)                              ║
   ║  Base URL: testnet.binance.vision                          ║
   ║  API Key configured: YES                                   ║
   ╚════════════════════════════════════════════════════════════╝
   ✅ TESTNET MODE - Safe for testing with fake money
   ```

## Safety Features

### Startup Warnings

When in **production mode**, you'll see:
```
╔════════════════════════════════════════════════════════════╗
║  TRADING MODE: PRODUCTION (REAL MONEY!)                    ║
║  Base URL: api.binance.com                                 ║
║  API Key configured: YES                                   ║
╚════════════════════════════════════════════════════════════╝
⚠️  WARNING: PRODUCTION MODE - REAL MONEY AT RISK! ⚠️
⚠️  All trades will execute on the LIVE Binance exchange! ⚠️
```

### Credential Validation

The bot validates that the correct credentials are configured for the selected mode. If missing, you'll see:
```
❌ ERROR: No API key configured for production mode!
   Please set BINANCE_PRODUCTION_API_KEY and BINANCE_PRODUCTION_API_SECRET in your .env file
```

## Best Practices

### Development Workflow

1. **Always start in testnet mode:**
   ```bash
   TRADING_MODE=testnet
   ```

2. **Test thoroughly:**
   - Verify AI recommendations
   - Test order placement
   - Test stop-loss and take-profit orders
   - Monitor for at least 24 hours

3. **Only switch to production when confident:**
   - All tests pass
   - Risk management is working
   - Cooldown periods are enforced
   - Position sizing is correct

### Production Deployment

1. **Set production credentials:**
   ```bash
   TRADING_MODE=production
   BINANCE_PRODUCTION_API_KEY=your-real-key
   BINANCE_PRODUCTION_API_SECRET=your-real-secret
   ```

2. **Start with small amounts:**
   - Use minimum position sizes
   - Monitor closely for the first few trades
   - Gradually increase position size

3. **Monitor continuously:**
   - Check logs regularly
   - Verify trades on Binance
   - Monitor P&L
   - Watch for API errors

### Security

- **Never commit production credentials to git**
- **Use API key restrictions on Binance:**
  - Enable only "Enable Spot & Margin Trading"
  - Restrict to specific IP addresses if possible
  - Set withdrawal restrictions
- **Keep `.env` file secure**
- **Rotate API keys periodically**

## Switching Modes

To switch from testnet to production:

1. **Stop the application**
2. **Update `.env`:**
   ```bash
   TRADING_MODE=production
   ```
3. **Verify production credentials are set**
4. **Restart the application**
5. **Check startup logs for warnings**

To switch back to testnet:

1. **Stop the application**
2. **Update `.env`:**
   ```bash
   TRADING_MODE=testnet
   ```
3. **Restart the application**

## Troubleshooting

### "No API key configured" error

**Cause:** Credentials not set for the selected mode

**Solution:**
- For testnet: Set `BINANCE_TESTNET_API_KEY` and `BINANCE_TESTNET_API_SECRET`
- For production: Set `BINANCE_PRODUCTION_API_KEY` and `BINANCE_PRODUCTION_API_SECRET`

### "Invalid API key" error

**Cause:** Wrong credentials or API key restrictions

**Solution:**
- Verify API keys are correct
- Check Binance API key restrictions
- Ensure API key has "Enable Spot & Margin Trading" enabled

### Orders not executing

**Cause:** Using testnet credentials in production mode (or vice versa)

**Solution:**
- Verify `TRADING_MODE` matches your credentials
- Check startup logs to confirm the mode

## Code Reference

The trading mode is managed by:
- **Configuration:** `TradingModeConfig.java`
- **API Service:** `BinanceApiService.java` (uses `TradingModeConfig`)
- **Properties:** `application.properties` (defines the structure)

Example usage in code:
```java
@Autowired
private TradingModeConfig tradingModeConfig;

public void someMethod() {
    if (tradingModeConfig.isTestnet()) {
        log.info("Running in safe testnet mode");
    } else {
        log.warn("Running in PRODUCTION mode with real money!");
    }
    
    String apiKey = tradingModeConfig.getApiKey();
    String baseUrl = tradingModeConfig.getBaseUrl();
}
```
