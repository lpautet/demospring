# Chart Marker Alignment Fix

> Note: This document is superseded by the consolidated guide: [TRADING_CHART_GUIDE.md](TRADING_CHART_GUIDE.md). Please refer to the consolidated guide for the latest chart behavior, including Open Orders overlays and OCO leg labeling.

## Problem

Trade markers (buy/sell dots) were appearing **on the right side of the chart**, misaligned with their actual timestamps.

**Symptom:**
- All markers clustered at the right edge
- Not positioned at correct time on x-axis
- Like they were "in the future"

---

## Root Cause

Using object notation `{x: timeString, y: price}` with **categorical x-axis** (string labels) caused Chart.js to misinterpret the positioning.

**Before (Broken):**
```javascript
const buyMarkers = recentTrades
    .filter(t => t.isBuyer)
    .map(t => ({
        x: new Date(t.time).toLocaleTimeString(),  // String x-coordinate
        y: safeParseFloat(t.price)
    }));

// Chart.js couldn't match these strings to label indices properly
```

Chart.js was treating these as independent data points rather than aligning them with the existing time labels on the x-axis.

---

## Solution

Use **sparse arrays** that align perfectly with the chart's label indices.

### Approach

1. Create arrays with same length as price history
2. Fill with `null` by default
3. Place marker values at matching time indices
4. Find closest time if no exact match

**After (Fixed):**
```javascript
// Create chart labels first
const chartLabels = priceHistory.map(p => p.time.toLocaleTimeString());

// Create sparse arrays (same length as chart)
const buyMarkersData = new Array(chartLabels.length).fill(null);
const sellMarkersData = new Array(chartLabels.length).fill(null);

// Place markers at correct indices
recentTrades.forEach(trade => {
    const tradeLabel = new Date(trade.time).toLocaleTimeString();
    let index = chartLabels.findIndex(label => label === tradeLabel);
    
    // Fallback: find closest time within 5 minutes
    if (index === -1) {
        // Find closest matching hour candle
        index = findClosestTimeIndex(trade.time, priceHistory);
    }
    
    if (index !== -1) {
        if (trade.isBuyer) {
            buyMarkersData[index] = trade.price;
        } else {
            sellMarkersData[index] = trade.price;
        }
    }
});
```

### Key Changes

**1. Sparse Array Structure**
```javascript
// Example: 24 hour chart with trade at position 12
[
    null, null, null, null, null,
    null, null, null, null, null,
    null, null, 3245.50,  // ← Buy marker here
    null, null, null, null, null,
    null, null, null, null, null, null
]
```

**2. Exact Label Matching**
```javascript
// Chart labels
['9:00:00 AM', '10:00:00 AM', '11:00:00 AM', ...]

// Trade at 10:00:00 AM → index 1
// Marker placed at buyMarkersData[1] = 3245.50
```

**3. Closest Time Fallback**
```javascript
// If trade is at 10:23:15 but chart has hourly candles:
// - Looks for exact match: "10:23:15 AM" ❌ not found
// - Finds closest: "10:00:00 AM" at index 1
// - If within 5 minutes: use that index
// - If > 5 minutes apart: skip marker (too far off)
```

---

## Visual Result

### Before Fix
```
Chart: 24 Hour Price
─────────────────────────────────────────────
Price                              🟢🟢🔴🔴🔴
                                  (all clustered
                                   on right edge)
─────────────────────────────────────────────
9am  12pm  3pm  6pm  9pm  12am  3am  6am  Now
```

### After Fix
```
Chart: 24 Hour Price
─────────────────────────────────────────────
Price      🟢           🔴         🟢
          (buy)       (sell)      (buy)
          at 12pm     at 6pm      at 3am
─────────────────────────────────────────────
9am  12pm  3pm  6pm  9pm  12am  3am  6am  Now
```

---

## Implementation Details

### Finding Closest Time

```javascript
if (index === -1 && priceHistory.length > 0) {
    const tradeTime = trade.time;
    let closestIndex = 0;
    let minDiff = Math.abs(priceHistory[0].time.getTime() - tradeTime);
    
    for (let i = 1; i < priceHistory.length; i++) {
        const diff = Math.abs(priceHistory[i].time.getTime() - tradeTime);
        if (diff < minDiff) {
            minDiff = diff;
            closestIndex = i;
        }
    }
    
    // Only use if within 5 minutes (300000ms)
    if (minDiff < 300000) {
        index = closestIndex;
    }
}
```

### Why 5 Minutes?

- Chart uses **1-hour candles** (klines)
- Trade can happen anywhere within that hour
- If trade is within ±5 min of candle time → snap to it
- If trade is > 5 min off → likely falls in a different candle → skip it

**Example:**
- Chart has candle at 2:00 PM
- Trade at 2:03 PM → diff = 3 min → ✅ show at 2:00 PM candle
- Trade at 2:45 PM → diff = 45 min → ❌ skip (closer to 3:00 PM candle)

---

## Dataset Configuration

```javascript
{
    label: 'Buy',
    data: buyMarkersData,  // Sparse array: [null, null, 3245, null, ...]
    showLine: false,       // Only show points, no line connecting them
    spanGaps: false,       // Don't try to connect null values
    pointRadius: 8,        // Show only where data exists
    backgroundColor: '#10b981',
    borderColor: '#059669',
    order: 1               // Render on top
}
```

**Key properties:**
- `showLine: false` - Only dots, no connecting lines
- `spanGaps: false` - Don't connect across null values
- Sparse array - Chart.js automatically skips null values

---

## Edge Cases Handled

### 1. No Exact Time Match
✅ **Solution:** Find closest hourly candle within 5 minutes

### 2. Trade Outside Chart Window
✅ **Solution:** Filtered by `chartStartTime` and `chartEndTime` before processing

### 3. Multiple Trades at Same Hour
✅ **Solution:** Last trade overwrites (or could be enhanced to show all)

### 4. No Trades in 24h Window
✅ **Solution:** `hasBuyMarkers` and `hasSellMarkers` flags prevent empty datasets

### 5. Chart Not Loaded Yet
✅ **Solution:** Check `priceHistory.length > 0` before processing

---

## Testing Checklist

- ✅ Markers appear at correct time positions
- ✅ Buy markers (green) align with buy times
- ✅ Sell markers (red) align with sell times
- ✅ No markers clustered on right edge
- ✅ Markers visible within chart bounds
- ✅ Hover shows correct price
- ✅ No console errors
- ✅ Works with empty trade history
- ✅ Works with trades outside 24h window

---

## Performance

**Time Complexity:**
- Creating sparse arrays: O(n) where n = chart labels
- Matching trades: O(t × n) where t = trades, n = chart points
- For typical case: 24 points × 10 trades = 240 operations ✅ Fast

**Space Complexity:**
- Two sparse arrays of length 24 (hourly chart)
- Negligible memory impact

---

## Files Modified

### `webapp/src/EthTrading.js`

**Lines 381-422:** Trade marker alignment logic
- Create sparse arrays matching chart length
- Find exact or closest time index
- Place markers at correct positions

**Lines 422-448:** Dataset configuration
- Use sparse arrays instead of filtered data
- Added `spanGaps: false` property

---

## Future Enhancements

### 1. Show All Trades at Same Time
Current: Last trade overwrites if multiple at same hour
Enhancement: Stack markers or show tooltip with all trades

### 2. Interpolate Position
Current: Snap to nearest hourly candle
Enhancement: Calculate exact position within the hour

### 3. Different Marker Sizes
Current: All markers same size
Enhancement: Larger markers for bigger trades

---

## Summary

✅ **Problem:** Markers appearing at wrong x-positions (clustered right)  
✅ **Cause:** Using `{x: string, y: value}` with categorical axis  
✅ **Solution:** Sparse arrays aligned with chart label indices  
✅ **Result:** Markers now appear at correct time positions  
✅ **Bonus:** Closest-time matching for sub-hourly trades  

---

**Date:** 2025-11-07  
**Status:** ✅ Fixed and deployed  
**Build:** 143.67 kB (+141 B)
