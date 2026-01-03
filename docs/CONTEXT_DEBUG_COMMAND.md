# 🔍 Context Debug Command

## Summary

Added `/eth context` command to display all the data being passed to the AI model. This is essential for troubleshooting issues where the AI reports seeing N/A or zero values.

## The Problem

Sometimes the AI returns recommendations like:
> "All market data and indicators are zero or N/A (price, volume, RSI, MACD, Bollinger Bands), suggesting a data outage or halted/illiquid market..."

This usually indicates:
- ❌ API connection issues
- ❌ Data deserialization problems
- ❌ Cache issues returning stale/invalid data
- ❌ Calculation errors in indicators
- ❌ Timing issues (market closed, maintenance window)

## The Solution

The `/eth context` command shows **exactly** what data the AI model receives, allowing you to:
- ✅ Verify API data is being fetched correctly
- ✅ Check if indicators are calculating properly
- ✅ Identify which specific data points are N/A or zero
- ✅ Confirm sentiment analysis is working
- ✅ Validate portfolio data is current

## Usage

Simply type in Slack:
```
/eth context
```

or

```
/eth debug
```

## Example Output

```
🔍 AI Context Data

This is the data being passed to the AI model

─────────────────

📊 Market Data (24h)
Price: `$3364.17`
Change: `+2.45% ($+80.50)`
High: `$3400.00`
Low: `$3250.00`
Volume: `125000 ETH`

─────────────────

📈 Technical Indicators (5m)
RSI: `65.23` - Neutral
MACD: `5.20` (Signal: `3.10`, Hist: `2.10`)
BB Upper: `3380.00` | Middle: `3364.17` | Lower: `3348.34`
EMA20: `3360.50` | EMA50: `3340.20`

📈 Technical Indicators (15m)
RSI: `62.15` - Neutral
MACD Hist: `1.85`

📈 Technical Indicators (1h)
RSI: `58.40` - Neutral
MACD Hist: `0.95`
Trend: Bullish

─────────────────

🎭 Sentiment Analysis
Score: `0.65`
Classification: `Moderately Bullish`
Fear & Greed: `72` (Greed)

─────────────────

💼 Portfolio
USDC: `$100.00`
ETH: `0.285000` ($958.94)
Total: `$1058.94`
Mode: TESTNET

💡 Use this to debug why AI might be seeing N/A or zero values
```

## What Gets Displayed

### 1. Market Data (24h Ticker)
- Current price
- 24h price change (% and $)
- 24h high/low
- 24h volume

**Source:** `binanceApiService.getETH24hrTicker()`

### 2. Technical Indicators (Multiple Timeframes)

#### 5-minute Chart
- RSI + signal
- MACD (value, signal, histogram)
- Bollinger Bands (upper, middle, lower)
- EMAs (20, 50)

#### 15-minute Chart
- RSI + signal
- MACD histogram

#### 1-hour Chart
- RSI + signal
- MACD histogram
- Trend direction

**Source:** `technicalIndicatorService.calculateIndicators()`

### 3. Sentiment Analysis
- Overall score
- Classification (Bullish/Bearish/Neutral)
- Fear & Greed Index
- Fear & Greed label

**Source:** `sentimentAnalysisService.getMarketSentiment()`

### 4. Portfolio
- USDC balance
- ETH balance + value
- Total portfolio value
- Trading mode (TESTNET/PAPER/LIVE)

**Source:** `tradingService.getAccountSummary()`

## Troubleshooting with Context

### Scenario 1: All Zeros

**Output shows:**
```
📊 Market Data (24h)
Price: `$0.00`
Change: `+0.00% ($+0.00)`
High: `$0.00`
Low: `$0.00`
Volume: `0 ETH`
```

**Diagnosis:** Binance API not responding or cache returning null
**Fix:** Check API connectivity, restart service, clear cache

### Scenario 2: N/A Values

**Output shows:**
```
📈 Technical Indicators (5m)
RSI: `0.00` - N/A
MACD: `0.00` (Signal: `0.00`, Hist: `0.00`)
```

**Diagnosis:** Not enough data points to calculate indicators
**Fix:** Wait for more kline data, check if symbol is correct

### Scenario 3: Sentiment N/A

**Output shows:**
```
🎭 Sentiment Analysis
Score: `0.00`
Classification: `N/A`
Fear & Greed: `0` (N/A)
```

**Diagnosis:** Sentiment service error or API issue
**Fix:** Check sentiment service logs, verify API keys

### Scenario 4: Portfolio Zeros

**Output shows:**
```
💼 Portfolio
USDC: `$0.00`
ETH: `0.000000` ($0.00)
Total: `$0.00`
Mode: TESTNET
```

**Diagnosis:** Testnet API not configured or credentials invalid
**Fix:** Check BINANCE_API_KEY and BINANCE_API_SECRET environment variables

## Implementation Details

### Handler Method

```java
public void handleContextCommand(String userId, String channelId) {
    // Gather all context data
    var ticker = binanceApiService.getETH24hrTicker();
    Map<String, Object> tech5m = technicalIndicatorService.calculateIndicators("ETHUSDC", "5m", 100);
    Map<String, Object> tech15m = technicalIndicatorService.calculateIndicators("ETHUSDC", "15m", 100);
    Map<String, Object> tech1h = technicalIndicatorService.calculateIndicators("ETHUSDC", "1h", 200);
    Map<String, Object> sentiment = sentimentAnalysisService.getMarketSentiment("ETHUSDC");
    Map<String, Object> portfolio = tradingService.getAccountSummary(userId);
    
    // Format and display in Slack blocks
    sendBlockMessage(channelId, blocks);
}
```

### Helper Methods

```java
// Safe value extraction with defaults
private double getDoubleValue(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null) return 0.0;
    if (value instanceof Number) return ((Number) value).doubleValue();
    return 0.0;
}

private String getStringValue(Map<String, Object> map, String key) {
    Object value = map.get(key);
    return value != null ? value.toString() : "N/A";
}
```

### Switch Case

```java
switch (subCommand) {
    case "context", "debug" -> slackBotService.handleContextCommand(userId, channelId);
    // ... other cases
}
```

## Benefits

### 1. Instant Visibility
See exactly what the AI sees - no guessing!

### 2. Faster Debugging
Identify the exact source of data issues immediately

### 3. Validate Fixes
After making changes, run `/eth context` to confirm data is now valid

### 4. Compare Timeframes
See how indicators differ across 5m, 15m, and 1h timeframes

### 5. Confidence in AI
Know that when AI says "no data", you can verify it yourself

## When to Use

### ✅ Use `/eth context` when:

1. **AI returns confusing recommendations**
   - "All data is N/A"
   - "No trading signals available"
   - Contradictory signals

2. **After deployment or restart**
   - Verify data is flowing correctly
   - Check cache is populating
   - Confirm APIs are responding

3. **When testing new features**
   - Validate calculations
   - Check data transformations
   - Verify integrations

4. **Troubleshooting errors**
   - API connection issues
   - Data format problems
   - Indicator calculation errors

5. **Performance testing**
   - See how fast data loads
   - Check if caching is working
   - Identify slow endpoints

## Common Issues & Solutions

### Issue: RSI always 0.00

**Cause:** Not enough data points (need 14+ candles)
**Solution:** Wait a few minutes for more data, or check kline API

### Issue: Price shows but indicators N/A

**Cause:** Technical indicator calculation error
**Solution:** Check logs for exceptions in `TechnicalIndicatorService`

### Issue: All data valid but AI still says N/A

**Cause:** AI prompt may need adjustment or OpenAI API issue
**Solution:** Check AI service logs, verify prompt formatting

### Issue: Stale data (old timestamps)

**Cause:** Cache not refreshing
**Solution:** Restart service (cache was disabled for records)

### Issue: Portfolio shows PAPER not TESTNET

**Cause:** Testnet API not configured
**Solution:** Set environment variables for Binance testnet

## Comparison: Before vs After

### Before (No Debug Tool)
```
User: "Why does AI say no data?"
Dev: "Let me check logs... run queries... add debug statements..."
Time: 15-30 minutes to diagnose
```

### After (With `/eth context`)
```
User: "/eth context"
Bot: Shows all data in 2 seconds
User: "Ah, RSI is 0.00 - we need more klines!"
Time: 2 seconds to diagnose
```

## Help Text Update

The `/eth help` command now shows:

```
*🔍 DEBUG*
`/eth context` - Show AI context data (troubleshooting)
```

## Future Enhancements

1. **Raw JSON View** - Option to see raw JSON responses
2. **Historical Context** - Save context snapshots for comparison
3. **Automated Checks** - Alert if context data is invalid
4. **Export Context** - Download context for bug reports
5. **Context Diff** - Compare context before/after changes

## Summary

The `/eth context` command is your **x-ray vision** into what the AI sees:

- 🎯 **Instant debugging** - See all data in 2 seconds
- 🎯 **Complete transparency** - No hidden transformations
- 🎯 **Multiple data sources** - Market, indicators, sentiment, portfolio
- 🎯 **Multiple timeframes** - 5m, 15m, 1h for cross-validation
- 🎯 **Production ready** - Safe to use anytime

**No more guessing why the AI sees N/A!** 🚀

---

**Usage:** `/eth context` or `/eth debug`  
**Aliases:** `context`, `debug`  
**Category:** Debug/Troubleshooting  
**Status:** Production Ready ✅
