# Debugging RecommendationHistory Component - NaN Issue

## Step 1: Open Browser Console

1. Open http://localhost:8080 in your browser
2. Press **F12** to open Developer Tools
3. Go to **Console** tab
4. Clear the console (trash icon)

## Step 2: Navigate to AI Memory Tab

1. Click "🧠 AI Memory" button
2. Watch the console for logs

## Expected Console Output

You should see:
```
RecommendationHistory component rendering...
RecommendationHistory mounted, fetching data...
Token: Present (or Missing)
fetchRecommendations called with limit: 20 filter: all
Fetching from URL: /api/trading/recommendations?limit=20
Response status: 200
Response ok: true
Fetched recommendations - Count: X
First recommendation: {...}
Raw data structure: {...}
```

## Step 3: Check What's Actually Happening

### Scenario A: No Console Logs at All
**Problem:** Component not rendering  
**Fix:** Check App.js routing
```javascript
// In browser console, type:
window.location.pathname
// Should show the current route
```

### Scenario B: Token Missing
**Console shows:** `Token: Missing`  
**Problem:** Not logged in  
**Fix:** Log in first, then navigate to AI Memory

### Scenario C: HTTP 401/403
**Console shows:** `Response status: 401`  
**Problem:** Authentication failed  
**Fix:** Re-login or check token validity

### Scenario D: HTTP 404
**Console shows:** `Response status: 404`  
**Problem:** API endpoint doesn't exist  
**Fix:** Verify backend is running with correct routes

### Scenario E: Empty Array
**Console shows:** `Fetched recommendations - Count: 0`  
**Problem:** No recommendations in database  
**Fix:** Generate some recommendations first

### Scenario F: Data Fetched But NaN Displayed
**Console shows:** Data fetched successfully  
**Problem:** Data format issue  
**Action:** Type in console:
```javascript
// Check the actual data structure
recommendations[0]
```

## Step 4: Manual API Test in Browser Console

```javascript
// Test API directly in browser console
fetch('/api/trading/recommendations?limit=5', {
    headers: {
        'Authorization': 'Bearer ' + sessionStorage.getItem('token')
    }
})
.then(r => {
    console.log('Status:', r.status);
    return r.json();
})
.then(data => {
    console.log('Data:', data);
    console.log('First item:', data[0]);
    console.log('Timestamp type:', typeof data[0]?.timestamp);
    console.log('Amount type:', typeof data[0]?.amount);
    console.log('Amount value:', data[0]?.amount);
})
.catch(err => console.error('Error:', err));
```

## Step 5: Check Backend Logs

```bash
# In terminal
tail -f logs/spring-boot-application.log | grep -i "recommendation"
```

Look for:
- `GET /api/trading/recommendations`
- Any errors or exceptions

## Step 6: Check Database

```sql
-- Check if recommendations exist
SELECT COUNT(*) FROM recommendation_history;

-- Check data format
SELECT 
    id,
    timestamp,
    signal,
    confidence,
    amount,
    amount_type,
    ai_memory,
    executed
FROM recommendation_history
LIMIT 1;
```

## Step 7: Generate Test Data

If database is empty:

### Via Slack:
```
/eth recommend
```

### Via API:
```bash
# This will trigger recommendation generation
curl -X POST http://localhost:8080/api/trading/recommend \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Wait for Automated Cycle:
The system generates recommendations every hour at x:00

## Step 8: Common Data Format Issues

### Issue 1: Timestamp as Number
```javascript
// If timestamp is epoch milliseconds
const date = new Date(1699276800000); // Works
const date = new Date("2025-11-06T13:00:00"); // Also works
```

### Issue 2: Amount as String
```javascript
// Backend might return as string
"amount": "0.00437"  // Need to parseFloat
"amount": 0.00437     // Already number
```

### Issue 3: JSON Fields
```javascript
// aiMemory might be JSON string
"aiMemory": "[\"item1\",\"item2\"]"  // Need JSON.parse
"aiMemory": ["item1", "item2"]        // Already array
```

## Step 9: Verify Component Receives Data

Add this temporarily at the top of the render:

```javascript
// In RecommendationHistory.js, add before return statement
console.log('Render with data:', {
    recommendationsLength: recommendations.length,
    firstRec: recommendations[0],
    statsLoaded: !!stats
});
```

## Step 10: Nuclear Option - Full Reset

If nothing works:

```bash
# 1. Stop the app
# 2. Clear all data
rm -rf target/
rm *.db  # if using H2

# 3. Rebuild
mvn clean install

# 4. Restart
mvn spring-boot:run

# 5. Generate a recommendation
# Slack: /eth recommend

# 6. Refresh browser
# Navigate to AI Memory tab
```

## Quick Checklist

- [ ] Browser console open
- [ ] See "RecommendationHistory component rendering"
- [ ] See "fetchRecommendations called"
- [ ] See "Response status: 200"
- [ ] See "Fetched recommendations - Count: X"
- [ ] No errors in console
- [ ] Backend running (check terminal)
- [ ] Database has data (check SQL)
- [ ] Token present in sessionStorage

## What to Share for Help

If still broken, share:

1. **Console output** (copy/paste all logs)
2. **Network tab** (click on the API request, show Response)
3. **Backend logs** (last 20 lines with "recommendation")
4. **Database query result** (SELECT * FROM recommendation_history LIMIT 1)
5. **Debug panel values** (from the gray box at top)

## Expected Working Flow

```
User clicks "AI Memory" tab
  ↓
Component renders
  ↓
useEffect triggers
  ↓
Fetch API called
  ↓
Backend returns JSON
  ↓
Console shows data
  ↓
State updated
  ↓
Component re-renders with data
  ↓
Table displays recommendations
```

If ANY step fails, we can pinpoint the exact issue!
