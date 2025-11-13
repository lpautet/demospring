# Testing Recommendation History API

## Quick Test Commands

### 1. Test API Endpoint
```bash
# Test recommendations endpoint
curl -X GET "http://localhost:8080/api/trading/recommendations?limit=5" \
  -H "Authorization: Bearer YOUR_TOKEN" | jq

# Test stats endpoint  
curl -X GET "http://localhost:8080/api/trading/recommendations/stats" \
  -H "Authorization: Bearer YOUR_TOKEN" | jq
```

### 2. Check Database Directly
```sql
-- Check if recommendations exist
SELECT COUNT(*) FROM recommendation_history;

-- View recent recommendations
SELECT 
    id,
    timestamp,
    signal,
    confidence,
    amount,
    ai_memory,
    executed
FROM recommendation_history
ORDER BY timestamp DESC
LIMIT 5;

-- Check data types
SELECT 
    id,
    pg_typeof(ai_memory) as memory_type,
    ai_memory,
    pg_typeof(execution_result) as exec_type,
    execution_result
FROM recommendation_history
WHERE id = (SELECT MAX(id) FROM recommendation_history);
```

### 3. Common Issues & Fixes

#### Issue: NaN in amounts
**Cause:** Amount field is null or not a valid number  
**Fix:** Added null checks and parseFloat validation in React component

#### Issue: Invalid Date
**Cause:** Timestamp is null, undefined, or invalid format  
**Fix:** Added try-catch with isNaN check for date parsing

#### Issue: Memory/Execution not displaying
**Cause:** JSON fields stored as strings in database  
**Fix:** Added JSON.parse() with fallback in React component

### 4. Frontend Console Debugging

Open browser console and check for:

```javascript
// Should see fetched data
"Fetched recommendations:" [{...}]

// Check data structure
recommendations[0].aiMemory  // Should be array or string
recommendations[0].executionResult  // Should be object or string
recommendations[0].timestamp  // Should be valid date string
```

### 5. Expected Data Format

**Good Response:**
```json
[
  {
    "id": 1,
    "timestamp": "2025-11-06T13:00:00",
    "signal": "SELL",
    "confidence": "HIGH",
    "amount": 0.00437,
    "amountType": "ETH",
    "reasoning": "Market overbought...",
    "aiMemory": ["Taking profit", "RSI high"],
    "executed": true,
    "executionResult": {
      "orderId": "12345",
      "executedQty": "0.00437",
      "avgPrice": "3200.00",
      "status": "FILLED"
    }
  }
]
```

**Alternate Format (if converters store as JSON strings):**
```json
[
  {
    "id": 1,
    "timestamp": "2025-11-06T13:00:00",
    "signal": "SELL",
    "aiMemory": "[\"Taking profit\",\"RSI high\"]",
    "executionResult": "{\"orderId\":\"12345\",...}"
  }
]
```

Both formats are now handled by the React component!

### 6. Manual Test Steps

1. **Start application**
   ```bash
   mvn spring-boot:run
   ```

2. **Generate a recommendation**
   - Via Slack: `/eth recommend`
   - Or wait for automated cycle

3. **Check web UI**
   - Navigate to http://localhost:8080
   - Click "🧠 AI Memory" tab
   - Should see recommendations table

4. **Open browser console** (F12)
   - Check for "Fetched recommendations" log
   - Verify no errors

5. **Test filters**
   - Try different limits (10, 20, 50)
   - Try different filters (All, Executed, BUY, SELL, HOLD)

6. **Expand details**
   - Click "▶ Details" on a row
   - Verify reasoning displays
   - Check if AI Memory shows
   - Check execution details (if executed)

### 7. Known Working Scenarios

✅ **Scenario 1:** Fresh database, no recommendations
- Shows empty state message
- Stats show all zeros

✅ **Scenario 2:** Recommendations with null amounts (HOLD signals)
- Displays "—" instead of NaN

✅ **Scenario 3:** JSON fields as strings
- Automatically parsed with JSON.parse()

✅ **Scenario 4:** Invalid/null timestamps
- Shows "N/A" or "Invalid date" instead of crashing

### 8. If Still Seeing Issues

Check these:

1. **Browser Console Errors**
   ```
   Look for JavaScript errors
   Check network tab for 4xx/5xx responses
   ```

2. **Server Logs**
   ```bash
   tail -f logs/spring-boot-application.log | grep -i error
   ```

3. **Database Query**
   ```sql
   -- Verify recommendations exist
   SELECT * FROM recommendation_history LIMIT 1;
   ```

4. **Authentication**
   ```
   Make sure you're logged in
   Check sessionStorage.getItem("token") in console
   ```

### 9. Reset and Retry

If all else fails:

```bash
# 1. Stop application
# 2. Clear database (if using H2)
rm -f *.db

# 3. Restart
mvn clean spring-boot:run

# 4. Generate fresh recommendation
# Slack: /eth recommend

# 5. Check web UI again
```

---

## Summary of Fixes Applied

1. ✅ Added null checks for timestamps
2. ✅ Added isNaN validation for date parsing  
3. ✅ Added parseFloat validation for amounts
4. ✅ Added JSON.parse() for string JSON fields
5. ✅ Added try-catch for all parsing operations
6. ✅ Added console.log for debugging
7. ✅ Added fallback values ("N/A", "—", etc.)

**Result:** Component now handles all edge cases gracefully!
