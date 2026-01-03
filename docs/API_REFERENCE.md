# Trading API Reference

Base URL: `/api/trading`

## Market Data
- `GET /eth/price`
  - Current price for ETHUSDC.

- `GET /eth/ticker24h`
  - 24h ticker statistics (price change, volume, high/low).

- `GET /eth/klines`
  - Query params:
    - `symbol` (default: ETHUSDC)
    - `interval` one of: 1m, 3m, 5m, 15m, 30m, 1h, 2h, 4h, 6h, 8h, 12h, 1d, 3d, 1w, 1M (validated subset)
    - `limit` (1..1000, default 100)
  - Returns Binance-format arrays: `[[openTime, open, high, low, close, volume, closeTime, ...], ...]`

## Account & Orders
- `GET /portfolio`
  - Account summary (balances, value, stats).

- `GET /open-orders`
  - Query: `symbol` (default ETHUSDC)
  - Current open orders.

- `GET /fees`
  - Query: `symbol` (default ETHUSDC)
  - Maker/taker fees. Falls back to account commissions on testnet if SAPI `asset/tradeFee` is unavailable.

- `GET /order`
  - Query: `symbol` (default ETHUSDC), `orderId` or `clientOrderId` (one required)
  - Specific order details. Used by UI to classify OCO legs (TP vs SL).

- `POST /cancel-order`
  - Body: `{ symbol?, orderId?, clientOrderId? }` (one of orderId/clientOrderId required)
  - Cancels an order.

- `GET /trades`
  - Recent trade executions.

- `GET /mode`
  - Trading mode info (TESTNET), for clarity in UI/logs.

- `GET /oco-status`
  - Query: `recId` (recommendation ID)
  - Refreshes and returns OCO child order status/type for a given recommendation.

## Recommendations
- `GET /recommendations`
  - Query: `limit` (default 20), `executed` (optional boolean), `signal` (BUY|SELL|HOLD)
  - Historical AI recommendations with optional filters.

- `GET /recommendations/stats`
  - Stats for the last 30 days (counts by signal, execution rate, totals).

## Notes
- Authentication: Bearer token (JWT) expected by the UI. Endpoints in this module assume a valid session.
- Environments: Uses Binance Testnet by default. Production requires configuration changes.
- Time fields in orders: UI prefers `updateTime` → `time` → `workingTime` → `transactTime` (ignores ≤0).

## Trading (execution)
- `POST /buy`
  - Body: `{ amount: number, reason?: string }` where amount is USD.
  - Executes a market BUY on testnet.

- `POST /sell`
  - Body: `{ amount: number, reason?: string }` where amount is ETH quantity.
  - Executes a market SELL on testnet.
