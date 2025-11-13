# ETH Trading Quick Start (Current)

This guide helps you run the ETH Trading module locally using Binance Testnet.

## Prerequisites
- Java 21
- Node.js 20+ and npm
- Binance Testnet API Key/Secret (see `docs/BINANCE_TESTNET_SETUP.md`)

## Configure Environment
Create/update `.env` in the project root:

```bash
# Binance Testnet
BINANCE_API_KEY=your_testnet_api_key
BINANCE_API_SECRET=your_testnet_secret
BINANCE_TESTNET=true

# JWT and app config (examples)
JWT_SECRET=your_256bit_secret
```

## Install Frontend
```bash
cd webapp
npm install
npm run build
cd ..
```

## Run Backend
```bash
mvn spring-boot:run
```

App is served at http://localhost:8080

## Use the Trading UI
1. Log in to obtain a session (JWT) if required by your setup.
2. Open the ETH Trading page in the UI.
3. You should see:
   - 24h price chart (line)
   - Portfolio and Current Price
   - Open Orders and Trade History
   - AI Trading Advisor

## What to Look For (Chart)
- Buy/Sell markers for your trades
- Average cost line (if applicable)
- Open Orders overlays:
  - TP lines: green dashed
  - SL lines: red dashed
- Realized OCO fills:
  - TP fills: green triangles
  - SL fills: orange triangles

## Notes
- Testnet often lacks some SAPI endpoints; fees fallback is handled automatically.
- If you see odd timestamps (e.g., 1970), the UI now guards against non-positive times and shows "—" instead.

## References
- Binance Testnet setup: `docs/BINANCE_TESTNET_SETUP.md`
- Trading chart behavior: `docs/TRADING_CHART_GUIDE.md`
- Endpoints: `docs/API_REFERENCE.md`
