# Duplicate Recommendation Fix

## Problem

When a recommendation was executed, the system was creating **two database entries**:
1. One when the recommendation was generated (executed=false)
2. Another when the trade was executed (executed=true)

**Example of duplicate entries:**
```
41m ago | SELL | HIGH | 0.0074 ETH | ✅  ← Executed entry
41m ago | SELL | HIGH | 0.0074 ETH | —   ← Original entry
```

---

## Root Cause

### Flow Before Fix

1. **`QuickRecommendationService.generateQuickRecommendation()`**
   - Generates AI recommendation
   - Saves to database with `executed=false`
   - Returns recommendation DTO (without database ID)

2. **`AutomatedTradingService.executeTrade()`**
   - Receives recommendation DTO
   - Executes trade on Binance
   - Calls `persistenceService.saveRecommendation(rec, true, order, null)`
   - **Creates NEW database entry** with `executed=true`

### The Bug
`saveRecommendation()` always created a new `RecommendationHistory` entity, even when called with `executed=true`. It had no way to know about the existing record.

---

## Solution

Updated `RecommendationPersistenceService` to:
1. **Find existing recommendation** when `executed=true`
2. **Update it** instead of creating a new one
3. **Log clearly** whether it's an update or new insert

### New Logic

```java
if (executed) {
    // Try to find matching recommendation from last 5 minutes
    history = findRecentMatchingRecommendation(recommendation);
    
    if (history != null) {
        // UPDATE existing record
        log.info("Found existing recommendation {} to update", history.getId());
    } else {
        // No match found - create new (shouldn't happen normally)
        log.warn("No matching recommendation found, creating new record");
        history = RecommendationHistory.fromRecommendation(recommendation);
    }
} else {
    // Normal flow - create new recommendation
    history = RecommendationHistory.fromRecommendation(recommendation);
}
```

### Matching Criteria

A recommendation matches if (within last 5 minutes):
- ✅ Same signal (BUY/SELL/HOLD)
- ✅ Same confidence (HIGH/MEDIUM/LOW)
- ✅ Same amount
- ✅ Not already executed

This ensures we update the correct recommendation even if multiple are generated.

---

## Changes Made

### File: `RecommendationPersistenceService.java`

#### 1. Added Matching Method (Lines 42-64)

```java
/**
 * Find the most recent unexecuted recommendation that matches this one
 * Used to update instead of creating duplicates
 */
private RecommendationHistory findRecentMatchingRecommendation(
        TradeRecommendation recommendation) {
    // Find recommendations from last 5 minutes
    var recentRecs = repository.findRecentSince(
        java.time.LocalDateTime.now().minusMinutes(5)
    );
    
    // Find matching unexecuted recommendation
    return recentRecs.stream()
        .filter(h -> !h.getExecuted())
        .filter(h -> h.getSignal() == recommendation.signal())
        .filter(h -> h.getConfidence() == recommendation.confidence())
        .filter(h -> {
            if (recommendation.amount() == null) return h.getAmount() == null;
            return recommendation.amount().compareTo(h.getAmount()) == 0;
        })
        .findFirst()
        .orElse(null);
}
```

#### 2. Updated Save Method (Lines 77-98)

```java
RecommendationHistory history;

// If this is an execution, try to find and update existing
if (executed) {
    history = findRecentMatchingRecommendation(recommendation);
    
    if (history != null) {
        log.info("Found existing recommendation {} to update", history.getId());
    } else {
        log.warn("No matching recommendation found, creating new record");
        history = RecommendationHistory.fromRecommendation(recommendation);
    }
} else {
    history = RecommendationHistory.fromRecommendation(recommendation);
}
```

#### 3. Improved Logging (Lines 125-137)

```java
boolean isUpdate = (history.getId() != null);
RecommendationHistory saved = repository.save(history);

if (isUpdate) {
    log.info("Updated recommendation {}: {} {} marked as executed", 
        saved.getId(), saved.getSignal(), saved.getAmount());
} else {
    log.info("Saved new recommendation: {} {} (executed: {}, id: {})", 
        saved.getSignal(), saved.getAmount(), saved.getExecuted(), saved.getId());
}
```

---

## Expected Behavior After Fix

### Before (Duplicates)
```
ID | Timestamp | Signal | Executed | Order ID
---+-----------+--------+----------+---------
10 | 14:00:00  | SELL   | true     | 801      ← Duplicate (execution)
9  | 14:00:00  | SELL   | false    | null     ← Original
8  | 13:00:00  | BUY    | true     | 795      ← Duplicate (execution)
7  | 13:00:00  | BUY    | false    | null     ← Original
```

### After (No Duplicates)
```
ID | Timestamp | Signal | Executed | Order ID
---+-----------+--------+----------+---------
9  | 14:00:00  | SELL   | true     | 801      ← UPDATED with execution
7  | 13:00:00  | BUY    | true     | 795      ← UPDATED with execution
6  | 12:00:00  | HOLD   | false    | null     ← Not executed
```

---

## Logs to Watch

### When Recommendation Generated
```
Saved new recommendation: SELL 0.0074 ETH (executed: false, id: 42)
```

### When Trade Executed
```
Found existing recommendation 42 to update with execution details
Updated recommendation 42: SELL 0.0074 ETH marked as executed
```

### If No Match Found (Warning)
```
No matching recommendation found for execution, creating new record
Saved new recommendation: SELL 0.0074 ETH (executed: true, id: 43)
```

---

## Testing

### Manual Test

1. **Generate recommendation:**
   ```
   /eth recommend
   ```
   Check logs: `Saved new recommendation: ... (id: X)`

2. **Execute trade (if HIGH confidence):**
   Wait for automated execution or trigger manually

3. **Check logs:**
   Should see: `Found existing recommendation X to update`

4. **Check database:**
   ```sql
   SELECT id, timestamp, signal, confidence, amount, executed, 
          execution_result->>'orderId' as order_id
   FROM recommendation_history
   ORDER BY timestamp DESC
   LIMIT 10;
   ```
   Should see NO duplicates with same timestamp and signal

### Slack Command Test

```
/eth recommendations 10
```

**Before Fix:**
- Shows pairs of identical recommendations (one with ✅, one with —)

**After Fix:**
- Shows single entry with ✅ for executed trades
- No duplicate timestamps

---

## Edge Cases Handled

### 1. Multiple Recommendations in Same Minute
✅ Matches by signal + confidence + amount, not just time

### 2. Recommendation Already Executed
✅ `findRecentMatchingRecommendation()` filters out executed ones

### 3. No Matching Recommendation Found
✅ Creates new record with warning log (safety fallback)

### 4. Recommendation Older Than 5 Minutes
✅ Won't match (execution should happen quickly)

### 5. HOLD Signal (Never Executed)
✅ Always stays as single record with `executed=false`

---

## Verification Queries

### Check for Duplicates (Should Return 0)
```sql
SELECT 
    timestamp,
    signal,
    confidence,
    amount,
    COUNT(*) as count
FROM recommendation_history
WHERE timestamp > NOW() - INTERVAL '1 hour'
GROUP BY timestamp, signal, confidence, amount
HAVING COUNT(*) > 1;
```

### See Execution Updates
```sql
SELECT 
    id,
    timestamp,
    signal,
    executed,
    execution_result->>'orderId' as order_id,
    execution_result->>'status' as status
FROM recommendation_history
WHERE executed = true
ORDER BY timestamp DESC
LIMIT 10;
```

### Count Per Hour (Should Match Recommendation Frequency)
```sql
SELECT 
    DATE_TRUNC('hour', timestamp) as hour,
    COUNT(*) as recommendations,
    SUM(CASE WHEN executed THEN 1 ELSE 0 END) as executed_count
FROM recommendation_history
WHERE timestamp > NOW() - INTERVAL '24 hours'
GROUP BY hour
ORDER BY hour DESC;
```

---

## Rollback Plan (If Needed)

If issues arise, revert to always creating new records:

```java
// In saveRecommendation() method, replace lines 77-95 with:
RecommendationHistory history = RecommendationHistory.fromRecommendation(recommendation);
```

This goes back to the old behavior (with duplicates).

---

## Future Improvements

### Consider Adding:

1. **Recommendation ID in DTO**
   - Return ID from `generateQuickRecommendation()`
   - Pass ID to automated trading service
   - Use `updateExecutionResult()` with specific ID

2. **Stricter Matching**
   - Match by reasoning text hash
   - Track recommendation UUID

3. **Audit Trail**
   - Add `updated_at` timestamp
   - Track number of updates

4. **Cleanup Old Unexecuted**
   - Delete unexecuted recommendations older than 1 day
   - Prevents stale matches

---

## Summary

✅ **Problem:** Duplicate recommendation entries on execution  
✅ **Cause:** Always creating new record instead of updating  
✅ **Solution:** Find and update existing recommendation  
✅ **Status:** Fixed and tested  
✅ **Verification:** Check Slack `/eth recommendations` command  

---

**Date:** 2025-11-06  
**Status:** ✅ Fixed  
**Backwards Compatible:** Yes  
**Breaking Changes:** None
