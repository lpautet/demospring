# Trading Memory System

## Overview

The Trading Memory System gives the AI **persistent memory** across recommendations, enabling:
- **Consistency** - Prevent flip-flopping between signals
- **Context continuity** - Remember hypotheses and patterns being tracked
- **Learning** - Track what works and adapt strategies
- **Performance** - ~200 tokens vs 2000+ for full history (90% savings!)

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Automated Trading Cycle                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│         QuickRecommendationService                          │
│  1. Gather market data (price, indicators, sentiment)       │
│  2. Get AI's previous memory ◄── TradingMemoryService       │
│  3. Call LLM with full context                              │
│  4. Parse structured recommendation                          │
│  5. Persist recommendation ──► RecommendationPersistenceService
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              RecommendationHistory                           │
│  - Signal, Confidence, Amount                                │
│  - Reasoning                                                 │
│  - AI Memory (working notes) ◄── Feeds back to next cycle   │
│  - Execution details (if trade executed)                     │
└─────────────────────────────────────────────────────────────┘
```

## Components

### 1. TradeRecommendation DTO (Updated)

**New field:**
```java
List<String> memory  // AI's working memory (max 3 bullet points)
```

The AI populates this with notes like:
- "Watching for breakout above $3,250 with volume confirmation"
- "Entered consolidation phase 6h ago - waiting for catalyst"
- "If support breaks $3,150, will reassess bearish scenario"

### 2. RecommendationHistory Entity

**Stores:**
- All recommendation fields (signal, confidence, amount, reasoning)
- `aiMemory` - AI's working memory (JSON array)
- `executed` - Boolean flag
- `executionResult` - Trade details if executed (JSON)
- `marketContext` - Market snapshot (JSON, future use)
- `timestamp` - For chronological ordering

**Key features:**
- Indexed for fast queries (timestamp, executed, signal)
- JSON converters for PostgreSQL/H2 compatibility
- Audit trail for all decisions

### 3. TradingMemoryService

**Purpose:** Build compact, token-efficient context from history

**Output format (~200 tokens):**
```
📊 TRADING MEMORY (Last 24h)

Your Previous Memory:
[2h ago]
  • Watching for breakout above $3,250 with volume
  • RSI neutral consolidation for 12h
  • Support break at $3,150 would shift thesis

Recent Activity:
  • [2h ago] HOLD - Price consolidating near support
  • [6h ago] BUY ✅ - Oversold RSI + sentiment shift
  • [12h ago] HOLD - Mixed signals, waiting

Performance Context:
2 trades executed in last 24h
```

**Key methods:**
- `getTradingMemoryContext()` - Full summary for AI prompt
- `getAIMemory()` - Previous recommendation's memory
- `getRecentActivity()` - Significant events only (not every HOLD)
- `getPerformanceSummary()` - Trade count, P&L context

**Smart filtering:**
- Only shows signal changes and executions
- Compresses sequences of HOLDs into "Consistent HOLD signal"
- Limits to last 3 significant events
- Human-readable time formatting ("2h ago", "15m ago")

### 4. RecommendationPersistenceService

**Purpose:** Save recommendations with transaction management

**Key methods:**
```java
// Save recommendation without execution
saveRecommendation(TradeRecommendation rec)

// Save with execution details
saveRecommendation(rec, executed, orderResponse, marketContext)

// Update existing with execution result
updateExecutionResult(recommendationId, orderResponse)
```

**Features:**
- Validates and truncates memory to max 3 items
- Non-critical error handling (doesn't fail main flow)
- Optional cleanup of old records (keeps last 1000)

### 5. RecommendationHistoryRepository

**Custom queries:**
- `findFirstByOrderByTimestampDesc()` - Most recent (for AI memory)
- `findRecentSince(LocalDateTime)` - Last N hours
- `findExecutedRecommendations()` - Actual trades only
- `findBySignalSince(signal, since)` - Filter by signal type
- `countSince(since)` - Statistics

## Flow

### Recommendation Generation

```
1. User triggers recommendation
   └─► QuickRecommendationService.getQuickRecommendation()

2. Gather market context
   - Price data
   - Technical indicators (5m, 15m, 1h)
   - Sentiment analysis
   - Portfolio status
   └─► TradingMemoryService.getTradingMemoryContext()
       - Previous AI memory
       - Recent activity summary
       - Performance context

3. Call LLM with structured output
   - Prompt includes trading memory section
   - BeanOutputConverter ensures JSON schema
   - LLM returns: signal, confidence, amount, reasoning, memory

4. Persist recommendation
   └─► RecommendationPersistenceService.saveRecommendation()
       - Saves to database
       - Memory becomes available for next cycle

5. Return to caller
```

### Trade Execution (Automated)

```
1. AutomatedTradingService gets recommendation
2. Evaluates if should execute
   - isActionable() && (MEDIUM or HIGH confidence)
3. If executing:
   - Place order on Binance
   - Save execution record
   └─► persistenceService.saveRecommendation(rec, true, order, null)
4. Report to Slack
```

## Example AI Memory Evolution

### Cycle 1 (First analysis)
```json
{
  "signal": "HOLD",
  "confidence": "MEDIUM",
  "memory": [
    "Price forming potential double bottom near $3,150",
    "Volume declining - need spike for confirmation",
    "Key resistance at $3,250"
  ]
}
```

### Cycle 2 (2h later - breakout starting)
```json
{
  "signal": "BUY",
  "confidence": "HIGH",
  "amount": 50.00,
  "amountType": "USD",
  "memory": [
    "Double bottom CONFIRMED - broke $3,200 with 2.3x volume",
    "First target $3,250 resistance",
    "Stop loss below $3,150 invalidates pattern"
  ]
}
```

### Cycle 3 (2h later - position management)
```json
{
  "signal": "HOLD",
  "confidence": "HIGH",
  "memory": [
    "Holding position from $3,185 entry",
    "Approaching $3,250 resistance - watching for breakout or rejection",
    "RSI 58 - room to run but will take profit if overbought"
  ]
}
```

### Cycle 4 (1h later - profit taking)
```json
{
  "signal": "SELL",
  "confidence": "HIGH",
  "amount": 0.0156,
  "amountType": "ETH",
  "memory": [
    "Taking profit at $3,248 (+$10 gain from entry)",
    "RSI reached 72 - overbought on 15m chart",
    "Will re-enter on pullback to $3,200 support"
  ]
}
```

## Token Economics

### Before (Full History)
```
24 recommendations × ~100 tokens each = 2,400 tokens
+ Reasoning text = 3,000+ total tokens
Cost: High
Information: Verbose, noisy
```

### After (Compressed Memory)
```
AI memory (3 bullets)      = 50 tokens
Recent activity (3 events) = 100 tokens
Performance summary        = 50 tokens
Total                      = 200 tokens
Cost: 93% reduction!
Information: Relevant, actionable
```

## Database Schema

### Table: `recommendation_history`

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| timestamp | TIMESTAMP | When generated |
| signal | VARCHAR(10) | BUY/SELL/HOLD |
| confidence | VARCHAR(10) | HIGH/MEDIUM/LOW |
| amount | DECIMAL(20,8) | Trade amount |
| amount_type | VARCHAR(10) | USD/ETH/NONE |
| reasoning | TEXT | AI explanation |
| ai_memory | TEXT | JSON array of memory items |
| executed | BOOLEAN | Was trade executed? |
| execution_result | TEXT | JSON with order details |
| market_context | TEXT | JSON market snapshot |

**Indexes:**
- `timestamp DESC` - Recent lookups
- `executed` - Filter trades
- `signal` - Filter by action

## Configuration

### Automatic Setup

With `spring.jpa.hibernate.ddl-auto=update`, the schema is auto-created on startup.

### Manual Setup (Production)

```sql
-- Run schema_recommendation_history.sql
psql -d your_database -f src/main/resources/db/schema_recommendation_history.sql
```

## Benefits

### 1. Consistency
❌ **Before:** BUY → HOLD → SELL → BUY (erratic)  
✅ **After:** BUY → HOLD → HOLD → SELL (coherent)

AI remembers: "Entered position at $3,200, watching $3,250 target"

### 2. Learning
AI tracks hypotheses:
- "Similar setup 2 days ago led to +3% move" ✅
- "Volume spike preceded breakout" ✅
- "False breakout at $3,280 last week" ⚠️

### 3. Risk Management
AI maintains awareness:
- "Stop loss at $3,150 if support breaks"
- "Already 15% portfolio in ETH - limit new positions"
- "Last 3 trades were profitable - don't overtrade"

### 4. Performance
- **90% token reduction** (200 vs 2000+)
- **Faster responses** (less to process)
- **Lower costs** (fewer input tokens)

## Monitoring

### View Recent Recommendations
```java
repository.findTop10ByOrderByTimestampDesc()
```

### Check AI Memory Evolution
```java
var recent = repository.findRecentSince(LocalDateTime.now().minusHours(24));
recent.forEach(r -> {
    System.out.println(r.getSignal() + ": " + r.getAiMemory());
});
```

### Execution Rate
```java
long total = repository.countSince(cutoff);
long executed = repository.findExecutedRecommendations().size();
double rate = (double) executed / total * 100;
System.out.println("Execution rate: " + rate + "%");
```

## Future Enhancements

### 1. Performance Tracking
Add P&L tracking:
```java
@Column
private BigDecimal profitLoss;

@Column  
private BigDecimal entryPrice;
```

### 2. Market Regime Detection
Track market phases:
```java
public enum MarketRegime { TRENDING, CONSOLIDATION, VOLATILE }
```

### 3. Pattern Recognition
Learn from similar setups:
```sql
SELECT * FROM recommendation_history
WHERE signal = 'BUY'
AND executed = true
AND market_context->>'rsi' < '35'
ORDER BY timestamp DESC;
```

### 4. Multi-Asset Support
Extend to BTC, SOL, etc.:
```java
@Column
private String symbol;  // ETHUSDC, BTCUSDC, etc.
```

## Testing

### Manual Test
```bash
# Start application
mvn spring-boot:run

# Trigger recommendation (via Slack or API)
curl -X POST http://localhost:8080/api/trading/recommend

# Check database
psql -d your_db -c "SELECT signal, confidence, ai_memory FROM recommendation_history ORDER BY timestamp DESC LIMIT 5;"
```

### Verify Memory Persistence
```java
@Test
void testMemoryPersistence() {
    var rec = new TradeRecommendation(
        Signal.BUY, Confidence.HIGH, 
        new BigDecimal("100"), AmountType.USD,
        "Test reasoning",
        List.of("Memory item 1", "Memory item 2")
    );
    
    var saved = persistenceService.saveRecommendation(rec);
    assertThat(saved.getAiMemory()).hasSize(2);
    
    // Verify it's available for next cycle
    String memory = tradingMemoryService.getTradingMemoryContext();
    assertThat(memory).contains("Memory item 1");
}
```

## Troubleshooting

### Memory Not Showing Up
**Problem:** AI memory not in context  
**Check:**
1. Is recommendation being persisted? (check logs)
2. Is TradingMemoryService being called? (check logs)
3. Is database accessible? (check connection)

### Memory Truncated
**Symptom:** Only 3 items max  
**Cause:** Intentional - prevents memory bloat  
**Solution:** Acceptable behavior, AI should prioritize

### No Trading Memory on First Run
**Symptom:** "No previous memory (first analysis)"  
**Cause:** Database is empty  
**Solution:** Normal - will populate after first recommendation

## Summary

The Trading Memory System transforms the AI from stateless (each analysis independent) to **stateful** (maintains context over time), resulting in:

✅ **More consistent** recommendations  
✅ **Better risk management** awareness  
✅ **Token-efficient** context (~200 vs 2000+)  
✅ **Learning from experience** via pattern tracking  
✅ **Full audit trail** for compliance

**Status:** ✅ Fully implemented and tested  
**Next:** Monitor AI memory evolution in production
