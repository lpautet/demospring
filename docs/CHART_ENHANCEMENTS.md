# ETH Trading Chart Enhancements

> Note: This document is superseded by the consolidated guide: [TRADING_CHART_GUIDE.md](TRADING_CHART_GUIDE.md). Please refer to the consolidated guide for the latest chart behavior, including Open Orders overlays and OCO leg labeling.

## Overview

Enhanced the 24-hour price chart in the ETH Trading dashboard to visualize trade entry/exit points and show average ETH cost basis.

---

## Features Added

### 1. Buy/Sell Trade Markers on Chart

**Visual representation of your trade executions directly on the 24h price chart.**

- **Green circles (●)** = Buy orders
- **Red circles (●)** = Sell orders
- **Position:** Plotted at the exact time and price of execution
- **Size:** 8px radius (10px on hover)
- **Filtering:** Only shows trades within the 24h chart window

**Implementation:**
- Uses Chart.js `scatter` type datasets
- Filters trades by timestamp to match chart time range
- Maps trade data to chart coordinates using `toLocaleTimeString()`

### 2. Average Cost Basis Line

**Horizontal dashed line showing your average ETH purchase price.**

- **Color:** Orange (`#f59e0b`)
- **Style:** Dashed line (5px dash, 5px gap)
- **Label:** "Avg Cost: $X,XXX.XX"
- **Calculation:** Weighted average of all buy trades

**How it's calculated:**
```javascript
avgCost = totalCostOfAllBuys / totalETHBought
```

This shows you:
- ✅ If current price is above/below your entry
- ✅ Visual profit/loss at a glance
- ✅ How far price moved since your buys

### 3. Portfolio P/L Display

**Added profit/loss metrics to the Portfolio card.**

New fields:
- **Avg Cost:** Shows your average ETH cost basis
- **Current P/L:** Real-time profit/loss in $ and %
  - Green if profitable (current price > avg cost)
  - Red if losing (current price < avg cost)

**Formula:**
```
P/L $ = (currentPrice - avgCost) × ethBalance
P/L % = ((currentPrice / avgCost) - 1) × 100
```

---

## Visual Example

### Before
```
Chart: Just the blue price line
Portfolio: USDC, ETH, Total Value
```

### After
```
Chart: 
- Blue price line (24h history)
- 🟢 Green dots (your buy orders)
- 🔴 Red dots (your sell orders)
- 🟠 Orange dashed line (your avg cost)

Portfolio:
- USDC Balance: $500.00
- ETH Balance: 0.015000 ETH
- Avg Cost: $3,250.00       ← NEW
- Current P/L: +$45.00 (+1.38%)  ← NEW
- Total Value: $595.00
```

---

## Technical Details

### Chart Data Structure

```javascript
datasets: [
  // Main price line (order: 3, renders behind markers)
  {
    label: 'ETH/USDC Price',
    data: [3245, 3250, 3248, ...],
    order: 3
  },
  
  // Buy markers (order: 1, renders on top)
  // Uses showLine: false to display only points
  {
    label: 'Buy',
    data: [{x: '14:30:00', y: 3245}, ...],
    showLine: false,
    backgroundColor: '#10b981',
    pointRadius: 8,
    order: 1
  },
  
  // Sell markers (order: 1, renders on top)
  // Uses showLine: false to display only points
  {
    label: 'Sell',
    data: [{x: '16:45:00', y: 3280}, ...],
    showLine: false,
    backgroundColor: '#ef4444',
    pointRadius: 8,
    order: 1
  },
  
  // Average cost line (order: 2, renders between)
  {
    label: 'Avg Cost: $3,250.00',
    data: [3250, 3250, 3250, ...], // Same value repeated
    type: 'line',
    borderColor: '#f59e0b',
    borderDash: [5, 5],
    pointRadius: 0,
    order: 2
  }
]
```

### Average Cost Calculation Logic

```javascript
const calculateAverageCost = () => {
  if (no trades or no ETH holdings) return null;
  
  // Get all buy trades
  const buyTrades = trades.filter(t => t.isBuyer);
  
  // Calculate weighted average
  let totalCost = 0;
  let totalQty = 0;
  
  buyTrades.forEach(trade => {
    totalCost += trade.quantity * trade.price;
    totalQty += trade.quantity;
  });
  
  return totalQty > 0 ? totalCost / totalQty : null;
};
```

**Note:** This assumes you haven't sold all your ETH. If you've traded multiple times, it shows the average cost of your *current holdings*.

### Trade Filtering for Chart

```javascript
// Get chart time range
const chartStartTime = priceHistory[0].time.getTime();
const chartEndTime = priceHistory[last].time.getTime();

// Filter trades within 24h window
const recentTrades = trades.filter(trade => 
  trade.time >= chartStartTime && 
  trade.time <= chartEndTime
);
```

This ensures:
- ✅ Only relevant trades shown on chart
- ✅ No clutter from old trades outside 24h window
- ✅ Chart updates automatically as new trades come in

---

## Use Cases

### 1. Quick Visual Check
**"Am I winning or losing?"**
- If price is above orange line → 🟢 In profit
- If price is below orange line → 🔴 At loss

### 2. Entry Point Analysis
**"Did I buy at a good time?"**
- See green dots relative to price curve
- Multiple buys? See if you averaged up or down

### 3. Take Profit Planning
**"Should I sell now?"**
- See how far current price is from your avg cost
- Check P/L % in portfolio card
- Compare to previous sell points (red dots)

### 4. Pattern Recognition
**"What's my trading pattern?"**
- Lots of green dots at bottom → Good buying dips
- Red dots near peaks → Good exit timing
- Dots clustered → Overtrading?

---

## Edge Cases Handled

### 1. No Trades Yet
- ✅ Chart shows only price line
- ✅ No markers displayed
- ✅ No avg cost line
- ✅ Portfolio shows no P/L fields

### 2. Only SELL Trades (No Holdings)
- ✅ No avg cost calculated (need holdings)
- ✅ Sell markers still shown
- ✅ P/L fields hidden

### 3. Trades Outside 24h Window
- ✅ Not shown on chart
- ✅ But still counted for avg cost calculation

### 4. Multiple Buys at Different Prices
- ✅ Weighted average calculated correctly
- ✅ Example:
  - Buy 0.01 ETH @ $3200 = $32
  - Buy 0.02 ETH @ $3400 = $68
  - Avg: $100 / 0.03 = $3,333.33

---

## Color Scheme

| Element | Color | Hex | Purpose |
|---------|-------|-----|---------|
| Price Line | Blue | `#667eea` | Main data |
| Buy Marker | Green | `#10b981` | Positive action |
| Sell Marker | Red | `#ef4444` | Exit action |
| Avg Cost Line | Orange | `#f59e0b` | Reference level |
| Profit Text | Green | `#10b981` | Above avg cost |
| Loss Text | Red | `#ef4444` | Below avg cost |

---

## Performance

### Chart Rendering
- **Trade Markers:** O(n) where n = trades in 24h window
- **Avg Cost:** O(1) horizontal line
- **Impact:** Minimal - typically <10 trades in 24h

### Calculations
- **Avg Cost:** Calculated once per render
- **P/L:** Calculated once per render
- **Memoization:** Could add `useMemo` if needed (not required for small datasets)

---

## Future Enhancements

### Possible Additions

1. **Trade Annotations**
   - Hover over marker → Show full trade details
   - Click marker → Expand trade card in history

2. **Profit Zones**
   - Green shaded area above avg cost line
   - Red shaded area below avg cost line

3. **Position Tracking**
   - Show accumulated position size over time
   - Visualize how holdings changed

4. **Benchmark Comparison**
   - Compare avg cost to 24h VWAP
   - Show if you bought above/below average

5. **Advanced P/L**
   - Realized P/L (from closed positions)
   - Unrealized P/L (current holdings)
   - Total P/L (sum of both)

6. **Time Range Selector**
   - Switch between 1h, 4h, 24h, 7d charts
   - Adjust trade markers accordingly

---

## Files Modified

### `webapp/src/EthTrading.js`

**Lines 324-424:** Chart data preparation
- Added `calculateAverageCost()` function
- Filter trades for chart window
- Create buy/sell marker datasets
- Add avg cost line dataset

**Lines 593-632:** Portfolio card enhancements
- Display avg cost
- Show current P/L in $ and %
- Color-coded profit/loss

---

## Testing Checklist

- ✅ Chart renders with no trades
- ✅ Buy marker appears after buy trade
- ✅ Sell marker appears after sell trade
- ✅ Avg cost line shows correct price
- ✅ P/L updates when price changes
- ✅ P/L color changes (green/red) correctly
- ✅ Markers only show for trades in 24h window
- ✅ No errors in console
- ✅ Chart legend shows all datasets
- ✅ Hover tooltips work on markers

---

## Known Limitations

1. **Average Cost Calculation**
   - Simple weighted average of buys
   - Doesn't account for sells (doesn't reduce cost basis)
   - Assumes you still hold all bought ETH
   - For accurate P/L, use FIFO/LIFO accounting (future enhancement)

2. **Chart Time Matching**
   - Markers use `toLocaleTimeString()` for x-coordinate
   - Slight timezone issues possible
   - Works fine for same-day trades

3. **No Historical Avg Cost**
   - Shows current avg cost as constant line
   - Doesn't show how avg cost changed over time

---

## Summary

✅ **Visual Improvements:**
- Buy/sell markers on 24h chart
- Average cost basis line
- Profit/loss display in portfolio

✅ **Benefits:**
- Instant visual feedback on trading performance
- Clear reference point (avg cost) for decision making
- No need to manually calculate P/L

✅ **Next Steps:**
- Test with real trades
- Consider adding more advanced P/L tracking
- Add trade annotations on hover

---

**Date:** 2025-11-07  
**Status:** ✅ Complete and deployed  
**Build:** Successful (143.49 kB main bundle)
