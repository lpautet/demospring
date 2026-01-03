# 🔄 Restart Instructions - Technical Indicators Fix

## Why You Need to Restart

The technical indicator fixes **require a full application restart** because:
1. We changed how data is structured in `TechnicalIndicatorService`
2. Removed `@Cacheable` annotation (old cached values need to be cleared)
3. Added new calculations (EMA20, EMA50)

## How to Restart

### Option 1: Maven (Recommended)
```bash
# Stop current app (Ctrl+C if running)
mvn clean install
mvn spring-boot:run
```

### Option 2: If Using IDE
1. Stop the application
2. Clean/rebuild project
3. Run again

### Option 3: If Using Java directly
```bash
# Stop current app
mvn clean package
java -jar target/demospring-*.jar
```

## Verify the Fix

After restart, test with Slack:
```
/eth context
```

### What You Should See (GOOD)
```
Technical Indicators (5m)
RSI: 65.23 - Neutral ✅
MACD: 5.20 (Signal: 3.10, Hist: 2.10) ✅
BB Upper: 3380.00 | Middle: 3364.17 | Lower: 3348.34 ✅
EMA20: 3360.50 | EMA50: 3340.20 ✅
```

### What You're Currently Seeing (BEFORE RESTART)
```
Technical Indicators (5m)
RSI: 0.00 - N/A ❌
MACD: 0.00 (Signal: 0.00, Hist: 0.00) ❌
BB Upper: 0.00 | Middle: 0.00 | Lower: 0.00 ❌
EMA20: 0.00 | EMA50: 0.00 ❌
```

## Troubleshooting

### Still Seeing Zeros After Restart?

Check the application logs for:

```
INFO  TechnicalIndicatorService - Fetched X klines for 5m interval
```

#### If you see "Fetched 0 klines" or "Fetched < 50 klines":
- **Problem:** Binance API not returning enough data
- **Solution:** Check Binance API connectivity, or try a different interval

#### If you see error messages:
- Check logs for stack traces
- Verify Binance API is accessible
- Check if testnet is having issues

#### If logs show "Fetched 100 klines" but still zeros:
- There might be an exception being caught
- Check for error messages in logs
- The getDoubleValue helper might be returning 0.00 for missing keys

## Quick Diagnostic

### Check if changes were applied:
```bash
# Search for the new logging line
grep -r "Fetched.*klines for" src/
```

Should show the new log line in `TechnicalIndicatorService.java`

### Check if app is using new code:
```bash
# Check compiled class timestamp
ls -la target/classes/net/pautet/softs/demospring/service/TechnicalIndicatorService.class
```

Should be very recent (after your last build).

## Expected Behavior After Fix

1. **Market Data** ✅ Working (you can see price, volume)
2. **Technical Indicators** ✅ Should work after restart
3. **Sentiment** ❓ Might need separate investigation if still N/A
4. **Portfolio** ✅ Already working ($100.28 USDC)

## What We Fixed

### 1. Flattened Nested Maps
MACD, Bollinger Bands, and Stochastic now store flat values instead of nested Maps.

### 2. Added Missing Calculations  
Added EMA20 and EMA50 that were expected but missing.

### 3. Removed Cache
Removed `@Cacheable` to avoid Spring cache issues with records.

### 4. Added Logging
Added diagnostic logging to help troubleshoot data issues.

---

**Status:** Code is fixed, awaiting restart ✅  
**Action Required:** Restart application 🔄  
**Expected Result:** Working indicators! 🎯
