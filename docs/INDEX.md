# 📚 Documentation Index

Welcome to the ETH Trading Bot documentation! This index provides quick access to all guides and references.

---

## 🚀 Quick Start

Get up and running quickly:

- **[Quick Start Guide](QUICK_START.md)** - Run the application locally
- **[Quickstart Memory System](QUICKSTART_MEMORY.md)** - AI memory system in action
- **[Database Setup](DATABASE_SETUP.md)** - PostgreSQL configuration (NEW!)
- **[Slack Socket Mode Setup](QUICK_START_SOCKET_MODE.md)** - Configure Slack integration
- **[Binance Testnet Setup](BINANCE_TESTNET_SETUP.md)** - Configure testnet API keys
- **[AI Chat Setup](AI_CHAT_SETUP.md)** - Configure OpenAI integration
- **[Deployment Checklist](FINAL_DEPLOYMENT_CHECKLIST.md)** - Deploy to production

---

## 📖 Feature Guides

Learn how to use specific features:

- **[Function Calling Guide](FUNCTION_CALLING_GUIDE.md)** - AI function calling reference
- **[Quick Recommendations](QUICK_RECOMMENDATION_GUIDE.md)** - Fast AI trading analysis
- **[Context Debug Command](CONTEXT_DEBUG_COMMAND.md)** - Debug AI context data (NEW!)
- **[Testnet Reset Feature](TESTNET_RESET_FEATURE.md)** - Reset testnet account (NEW!)
- **[Reset with BTC Balance](RESET_WITH_BTC_SUMMARY.md)** - BTC balance adjustment logic (NEW!)

---

## 🏗️ Architecture & Design

Understand the system architecture:

- **[Slack Bot Design](SLACK_BOT_DESIGN.md)** - Bot architecture and patterns
- **[Context Service Refactor](CONTEXT_SERVICE_REFACTOR.md)** - Centralized context gathering (NEW!)
- **[OpenAI Model Selection](OPENAI_MODEL_SELECTION.md)** - Model selection rationale
- **[Cost Optimization](COST_OPTIMIZATION_COMPLETE.md)** - Strategies to reduce AI costs
- **[Production vs Testnet Data Requirements](PRODUCTION_VS_TESTNET_DATA_REQUIREMENTS.md)** - Environment-aware validation and data quality

## 🎨 Frontend & UI

User interface enhancements:

- **[Trading Chart Guide](TRADING_CHART_GUIDE.md)** - Consolidated guide with Open Orders overlays and OCO leg labeling (NEW!)
- **[Chart Enhancements](CHART_ENHANCEMENTS.md)** - Trading chart visualizations (NEW!)
- **[Chart Alignment Fix](CHART_ALIGNMENT_FIX.md)** - Marker positioning fix (NEW!)
- **[Recommendation History UI](RECOMMENDATION_HISTORY_UI.md)** - Historical recommendations display

---

## 🧩 API Reference

- **[Trading API Reference](API_REFERENCE.md)** - All `/api/trading` endpoints, params, and notes (UPDATED)

---

## 🗄️ Historical Documentation

Migration guides and implementation stories (useful for understanding how we got here):

- **[archive/](archive/)** - Contains 30+ historical documents:
  - Database migration (PostgreSQL setup, H2→PostgreSQL)
  - Bug fixes (NaN issues, trade history, duplicates)
  - Type-safe migration docs (Binance records refactoring)
  - Feature implementation stories
  - System evolution (Paper trading removal, integrations)
  - Slack bot development history
  - Session summaries and debugging notes
- **[Documentation Organization Plan](DOCS_ORGANIZATION_PLAN.md)** - How docs were organized

---

## 🔧 Available Slack Commands

### Analysis Commands
- `/eth recommend` - Quick trading recommendation (fast, cost-optimized)
- `/eth analyze` - Full market analysis with AI
- `/eth price` - Quick price check

### Portfolio Commands
- `/eth portfolio` - View portfolio & stats
- `/eth trades` - View trade history

### Trading Commands
- `/eth buy <amount>` - Buy ETH (e.g., /eth buy $500)
- `/eth sell <amount>` - Sell ETH (e.g., /eth sell 0.5)

### Testnet Commands
- `/eth reset` - Reset account to $100 USDC, 0 ETH

### Debug Commands
- `/eth context` - Show AI context data (troubleshooting)
- `/eth help` - Show all commands

---

## 🎯 Common Tasks

### First Time Setup
1. Read [Quick Start Guide](QUICK_START.md)
2. Configure [Binance Testnet](BINANCE_TESTNET_SETUP.md)
3. Setup [AI Chat](AI_CHAT_SETUP.md)
4. Configure [Slack Socket Mode](QUICK_START_SOCKET_MODE.md)

### Troubleshooting
- **AI returns "no data"?** → Use `/eth context` to debug
- **Need fresh start?** → Use `/eth reset`
- **High OpenAI costs?** → Read [Cost Optimization](COST_OPTIMIZATION_COMPLETE.md)

### Development
- Want to understand function calling? → [Function Calling Guide](FUNCTION_CALLING_GUIDE.md)
- Need to modify recommendations? → [Quick Recommendation Guide](QUICK_RECOMMENDATION_GUIDE.md)
- Looking at historical changes? → Check [archive/](archive/)

---

## 📊 System Architecture

```
┌─────────────────────────────────────────────┐
│           Slack Socket Mode                  │
│  (Real-time bidirectional communication)     │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│         Spring Boot Application              │
│                                              │
│  ┌────────────────────────────────────┐    │
│  │   SlackSocketModeService           │    │
│  │   SlackBotService                  │    │
│  └─────────────┬──────────────────────┘    │
│                │                             │
│  ┌─────────────▼──────────────────────┐    │
│  │   TradingService                    │    │
│  │   QuickRecommendationService        │    │
│  │   TradingChatService                │    │
│  └─────────────┬──────────────────────┘    │
│                │                             │
│  ┌─────────────▼──────────────────────┐    │
│  │   BinanceTestnetTradingService      │    │
│  │   BinanceApiService                 │    │
│  │   TechnicalIndicatorService         │    │
│  │   SentimentAnalysisService          │    │
│  └─────────────┬──────────────────────┘    │
└────────────────┼──────────────────────────┘
                 │
     ┌───────────┴────────────┐
     │                        │
┌────▼──────┐        ┌───────▼────────┐
│  Binance  │        │  OpenAI API    │
│  Testnet  │        │  (GPT-4o-mini) │
└───────────┘        └────────────────┘
```

---

## 🆘 Support

- **Documentation Issues?** Check [DOCUMENTATION_CLEANUP_PLAN.md](DOCUMENTATION_CLEANUP_PLAN.md)
- **Missing a guide?** Let us know what you need!
- **Found outdated info?** Please report it
- **Want to understand the organization?** See [ORGANIZATION_COMPLETE.md](ORGANIZATION_COMPLETE.md)

---

## 📝 Recent Updates

- **2025-11-08** - Documentation organization: consolidated database docs, moved historical files to archive
- **2025-11-12** - Chart: Added Open Orders overlays (TP/SL dashed lines) and OCO leg labeling (TP vs SL markers). New endpoint: `/api/trading/order`. Timestamp guard for open orders to avoid 1970 dates.
- **2025-11-07** - Context service refactoring: centralized context gathering, eliminated code duplication
- **2025-11-07** - Chart enhancements: buy/sell markers, average cost line, P/L display
- **2025-11-07** - ETH context fix: added AI memory to debug command
- **2025-11-05** - Added context debug command, testnet reset with BTC
- **2025-11-05** - PostgreSQL migration: replaced H2 with PostgreSQL for data persistence

---

**Current Status:** Production Ready ✅  
**Active Docs:** 20  
**Archived Docs:** 31  
**Total Features:** 15+ trading & analysis features
