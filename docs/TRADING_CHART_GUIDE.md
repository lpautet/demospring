# Trading Chart Guide (Consolidated)

This guide consolidates and supersedes:
- CHART_ENHANCEMENTS.md
- CHART_ALIGNMENT_FIX.md

It documents the current price chart behavior, including Open Orders overlays and OCO leg labeling.

## Overview
- Price line chart for last 24 hours (1h klines)
- Buy/Sell markers aligned to candle labels
- Average cost line (portfolio-based)
- Live Open Orders overlays (TP/SL dashed lines)
- Realized OCO fills with leg labeling (TP vs SL)

File: `webapp/src/EthTrading.js`

## Datasets
- Price line
  - label: `ETH/USDC Price`
  - color: blue `#667eea`
  - source: `/api/trading/eth/klines`

- Average cost line (optional)
  - label: `Avg Cost: $<value>`
  - color: orange `#f59e0b` (dashed)
  - logic: from portfolio buys; shown when applicable

- Buy markers (scatter)
  - label: `Buy`
  - color: green `#10b981`
  - source: `/api/trading/trades` (filter `isBuyer`)

- Sell markers (scatter)
  - label: `Sell`
  - color: red `#ef4444`
  - source: `/api/trading/trades` (filter `!isBuyer`)

- Open TP overlays (live orders)
  - labels: `Open TP #<n> ($<price>)`
  - color: green `#16a34a` (short dashed)
  - source: `/api/trading/open-orders` (filter: `side=SELL` and `type=LIMIT`)

- Open SL overlays (live orders)
  - labels: `Open SL #<n> ($<price>)`
  - color: red `#dc2626` (short dashed)
  - source: `/api/trading/open-orders` (filter: `side=SELL` and `type` contains `STOP`, use `stopPrice`)

- Realized OCO TP fills (scatter)
  - label: `OCO TP Fills`
  - color: green `#22c55e` triangle

- Realized OCO SL fills (scatter)
  - label: `OCO SL Fills`
  - color: orange `#f97316` triangle

## OCO Leg Labeling
- For each realized OCO sell (`!isBuyer` and `orderListId != null`), the UI fetches order details:
  - `GET /api/trading/order?symbol=ETHUSDC&orderId=<id>`
  - If `type=LIMIT` → TP; if `type` contains `STOP` → SL.
- Results cached in `orderTypeById` to minimize requests.

Backend support:
- Controller: `src/main/java/net/pautet/softs/demospring/rest/TradingController.java`
  - `GET /api/trading/order` added.
- DTO: `src/main/java/net/pautet/softs/demospring/dto/BinanceOrderResponse.java`
  - Added fields: `stopPrice`, `time`, `updateTime`.

## Time Alignment & Marker Placement
- Chart labels: `priceHistory.map(p => p.time.toLocaleTimeString())` (1h candles)
- Markers: sparse arrays matching labels length, filled with `null` except where a point exists.
- Exact match by label string; fallback finds closest candle within 5 minutes.
- Prevents right-edge clustering and ensures correct alignment.

## Timestamp Guard (avoiding 1970 dates)
- Some open orders may carry placeholder timestamps (0/-1) on testnet.
- Helper `getOrderTime(order)` chooses the first positive value among:
  - `updateTime`, `time`, `workingTime`, `transactTime`.
- Renders `—` if none available.

## Colors & Styles
- Price: blue `#667eea`
- Avg cost: orange `#f59e0b` dashed
- Buy: green dot `#10b981`
- Sell: red dot `#ef4444`
- Open TP: green line `#16a34a` short dashed
- Open SL: red line `#dc2626` short dashed
- OCO TP fills: green triangle `#22c55e`
- OCO SL fills: orange triangle `#f97316`

## Candlesticks Note
- We currently render a line chart (close prices). Candlestick plugin was removed due to package availability issues.
- If desired later, integrate `lightweight-charts` and reuse the same overlay logic.

## API Reference
See `docs/API_REFERENCE.md` for full endpoint details.

## Testing Checklist
- Price line renders
- Avg cost appears when holdings exist
- Buy/Sell markers align with labels
- Open TP/SL lines match live open orders
- Realized OCO fills colored by leg
- No 1970 dates in order cards

## Recent Changes
- Added `GET /api/trading/order` endpoint
- Added open order overlays (TP/SL)
- Added OCO leg labeling and timestamp guard
