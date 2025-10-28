# AI Function Calling - Netatmo Integration Guide

Your AI chatbot can now **directly query real-time Netatmo weather data** using Spring AI function calling! 🎉

## Overview

The AI assistant has been enhanced with three powerful tools:

1. **`getHomesData`** - Discover all homes, devices, and modules
2. **`getHomeStatus`** - Get real-time sensor readings
3. **`getMeasure`** - Retrieve historical measurements (last 24 hours)

## How It Works

### Architecture

```
User Question → AI → Determines needed data → Calls function → Gets JSON → Interprets → Returns friendly answer
```

**Example conversation:**
```
User: "What's the current temperature outside?"
AI: → Calls getHomesData(username) to find outdoor module
    → Calls getHomeStatus(username, homeId) to get current reading
    → Interprets JSON response
    → "The current outdoor temperature is 18.5°C"
```

### Components

#### 1. **NetatmoFunctions** (`NetatmoFunctions.java`)
Defines three `@Bean` functions that the AI can call:

- **getHomesData**: Returns complete home structure with all devices
  ```java
  Request: { "username": "user-id" }
  Response: { homes: [...], devices: [...], modules: [...] }
  ```

- **getHomeStatus**: Returns real-time sensor data
  ```java
  Request: { "username": "user-id", "homeId": "abc123" }
  Response: { temperature: 21.5, humidity: 45, co2: 600, ... }
  ```

- **getMeasure**: Returns historical time-series data
  ```java
  Request: {
    "username": "user-id",
    "deviceId": "device-id",
    "moduleId": "module-id",
    "types": ["Temperature", "Humidity"]
  }
  Response: { body: { [...time-series data...] } }
  ```

#### 2. **NetatmoApiService** (`NetatmoApiService.java`)
Low-level service that makes actual HTTP calls to Netatmo API:
- Handles authentication (uses user's access token)
- Manages timeouts and error handling
- Returns raw JSON responses

#### 3. **ChatService** (Updated)
Registers functions with ChatClient:
```java
ChatClient.builder(chatModel)
    .defaultFunctions("getHomesData", "getHomeStatus", "getMeasure")
    .build()
```

Injects username context so AI knows which user's data to query.

## Function Descriptions

The AI uses these descriptions to decide when and how to call functions:

### getHomesData
```
"Get all Netatmo homes data including devices, modules, rooms, and their 
configuration. Returns JSON with home structure, device types, module types, 
and basic information."
```

**When AI calls it:**
- User asks about available devices
- Need to discover home_id or device_id
- Want to see what sensors are available

### getHomeStatus
```
"Get real-time status of a specific Netatmo home by home ID. Returns JSON 
with current sensor readings (temperature, humidity, CO2, pressure), device 
status, and room information."
```

**When AI calls it:**
- User asks "what's the temperature?"
- Want current/live readings
- Need real-time sensor data

### getMeasure
```
"Get historical measurements from a Netatmo device or module. Returns 
time-series data for the requested measurement types over the last 24 hours 
in 30-minute intervals."
```

**When AI calls it:**
- User asks about trends or history
- Want to see temperature over time
- Need multiple data points

## JSON Schema Descriptions

Functions use `@JsonPropertyDescription` to guide the AI:

```java
@JsonPropertyDescription("The username/user ID to query data for")
String username

@JsonPropertyDescription("The home ID to get status for")
String homeId

@JsonPropertyDescription("Measurement types to retrieve (e.g., Temperature, Humidity, CO2)")
String[] types
```

This helps the AI understand:
- What parameters are required
- What format to use
- What values are valid

## System Prompt

The AI is instructed via system prompt:

```
You are a helpful AI assistant for a Netatmo weather station monitoring application.

You have access to real-time weather data through function calling tools:
- getHomesData: Get all homes, devices, and modules configuration
- getHomeStatus: Get current sensor readings
- getMeasure: Get historical measurements over the last 24 hours

When the user asks about weather data:
1. Use the function calls to fetch real data
2. The username parameter should ALWAYS be the current user's username
3. Interpret JSON responses and present data in a friendly way
4. Include units (°C, %, ppm, mbar)
5. If you need home_id or device_id, first call getHomesData

Available measurement types: Temperature, Humidity, CO2, Pressure, Noise, Rain
```

## Example Conversations

### Example 1: Current Temperature

**User:** "What's the temperature in the bedroom?"

**AI Process:**
1. Calls `getHomesData(username)` to find bedroom module
2. Extracts bedroom module's home_id and device_id
3. Calls `getHomeStatus(username, homeId)`
4. Parses JSON to find bedroom temperature
5. Responds: "The bedroom temperature is currently 20.8°C"

### Example 2: Temperature Trend

**User:** "Show me how the outdoor temperature changed today"

**AI Process:**
1. Calls `getHomesData(username)` to find outdoor module
2. Calls `getMeasure(username, deviceId, moduleId, ["Temperature"])`
3. Analyzes time-series data
4. Responds: "Today's outdoor temperature ranged from 12°C (morning) to 22°C (afternoon). Currently at 18°C and cooling down."

### Example 3: Multiple Sensors

**User:** "Give me a summary of all indoor conditions"

**AI Process:**
1. Calls `getHomesData(username)` to list all indoor modules
2. Calls `getHomeStatus(username, homeId)` for current readings
3. Summarizes all rooms
4. Responds: "Here's your indoor summary:
   - Living Room: 21.5°C, 45% humidity, 620ppm CO2
   - Bedroom: 20.8°C, 48% humidity, 580ppm CO2
   - Home Office: 22.1°C, 42% humidity, 710ppm CO2"

## Redis Caching

### How It Works

All AI function calls use **Spring Cache with Redis** - **using the exact same cache keys as REST endpoints**:

```java
@Cacheable(value = "homesdata", unless = "#result == null")
public String getHomesData(User user) { ... }

@Cacheable(value = "homestatus", 
           key = "'homeid:' + #homeId", 
           unless = "#result == null")
public String getHomeStatus(User user, String homeId) { ... }

@Cacheable(value = "measure",
           key = "'d:' + #deviceId + ',m:' + #moduleId + ',:s30min,t:' + T(java.util.Arrays).toString(#types)",
           unless = "#result == null")
public String getMeasure(User user, String deviceId, String moduleId, String[] types) { ... }
```

### Cache Keys

**AI functions and REST endpoints share the same cache keys:**
- `homesdata`: (no key - uses default SimpleKey)
- `homestatus`: `homeid:{homeId}`
- `measure`: `d:{deviceId},m:{moduleId},:s30min,t:{types array}`

### Cache Behavior

**First call (cache miss):**
```
User: "What's the temperature?"
AI → getHomeStatus() → Netatmo API call → Cache result → Return
Log: "Fetching homestatus for user: abc123 homeId: xyz789 (cache miss)"
```

**Subsequent calls (cache hit):**
```
User: "What about humidity?"
AI → getHomeStatus() → Return from cache (no API call)
Log: (no fetch log - cache hit)
```

### Cache TTL

Configured in `CacheConfig.java`:
- Default: 5 minutes (adjustable)
- Balances freshness vs API rate limits
- Same TTL for REST endpoints and AI functions

### Benefits

✅ **Shared cache** - REST API and AI functions use identical cache  
✅ **Reduced API calls** - Fewer requests to Netatmo  
✅ **Faster responses** - Cached data returns instantly  
✅ **Rate limit protection** - Prevents hitting Netatmo API limits  
✅ **Consistent data** - Dashboard and chat show same cached data  

### Monitoring Cache

Check Redis cache keys:
```bash
redis-cli
> KEYS homesdata::*
> KEYS homestatus::*
> KEYS measure::*
```

## Debugging Function Calls

### Enable Debug Logging

Add to `application.properties`:
```properties
logging.level.net.pautet.softs.demospring.service.NetatmoFunctions=DEBUG
logging.level.net.pautet.softs.demospring.service.ChatService=DEBUG
logging.level.net.pautet.softs.demospring.service.NetatmoApiService=DEBUG
```

### Watch for Log Messages

When AI calls a function, you'll see:

**Cache miss (API called):**
```
INFO  NetatmoFunctions : AI calling getHomeStatus for user: abc123 homeId: xyz789
DEBUG NetatmoApiService : Fetching homestatus for user: abc123 homeId: xyz789 (cache miss)
DEBUG NetatmoFunctions : getHomeStatus result: {"temperature":21.5,...}
```

**Cache hit (no API call):**
```
INFO  NetatmoFunctions : AI calling getHomeStatus for user: abc123 homeId: xyz789
DEBUG NetatmoFunctions : getHomeStatus result: {"temperature":21.5,...}
(No "cache miss" log - data served from Redis)
```

### Check Function Call Success

The AI will handle errors gracefully:
- If function returns `{"error": "..."}`, AI will explain the issue
- If API is down, returns helpful error message
- If no data available, explains why

## Limitations & Considerations

### Token Limits
- Each function call consumes tokens (prompt + response)
- Historical data (getMeasure) can be large
- Keep conversations focused to avoid hitting limits

### API Rate Limits & Caching
- **Redis caching enabled** - All function calls use the same Redis cache as REST endpoints
- Cached data reduces Netatmo API calls
- Cache keys include username for user isolation
- Cache TTL configured in `CacheConfig.java`
- Same cache used by both REST API and AI functions

### Authentication
- Functions require valid Netatmo access token
- If token expired, functions return error
- User must be authenticated with Netatmo

### Data Freshness
- getHomeStatus: Real-time (updates every ~5-10 min)
- getMeasure: Historical (30-min intervals)
- getHomesData: Configuration (rarely changes)

## Extending Function Calling

### Add New Functions

1. **Create function in NetatmoFunctions:**
```java
@Bean
@Description("Description for AI")
public Function<MyRequest, String> myFunction() {
    return request -> {
        // Implementation
        return jsonResult;
    };
}
```

2. **Register in ChatService:**
```java
.defaultFunctions("getHomesData", "getHomeStatus", "getMeasure", "myFunction")
```

3. **Update system prompt** to explain new function

### Common New Functions

Ideas for additional functions:
- **getWeatherForecast**: External weather API integration
- **setThermostatTemperature**: Control heating
- **getEnergyUsage**: Energy consumption stats
- **compareWithYesterday**: Day-over-day comparison
- **getAirQualityAlert**: Check if CO2 too high

## Testing

### Manual Testing

1. **Start application** with OpenAI API key set
2. **Navigate to Chat** page
3. **Ask questions** like:
   - "What's the temperature outside?"
   - "Show me all my sensors"
   - "How did the humidity change today?"
4. **Check logs** to see function calls

### Verify Function Registration

Check startup logs for:
```
Successfully registered functions: getHomesData, getHomeStatus, getMeasure
```

### Test Each Function

Test cases:
- ✅ "List all my devices" → should call getHomesData
- ✅ "What's the current temperature?" → should call getHomeStatus
- ✅ "Show temperature trend" → should call getMeasure

## Troubleshooting

### Function Not Called

**Problem:** AI doesn't call function when expected

**Solutions:**
1. Check system prompt mentions the function
2. Verify function is registered in `.defaultFunctions()`
3. Make sure `@Description` is clear and specific
4. Try rephrasing question to be more explicit

### Function Returns Error

**Problem:** Function returns `{"error": "..."}`

**Causes:**
- User not authenticated with Netatmo
- Invalid homeId or deviceId
- Netatmo API timeout
- Network issues

**Check:**
- User has valid access token in Redis
- homeId exists in user's account
- Netatmo API is accessible

### JSON Too Large

**Problem:** Function returns too much data

**Solutions:**
- Implement pagination in functions
- Filter/summarize data before returning
- Use smaller time windows for measurements
- Cache frequently accessed data

## Best Practices

1. **Clear descriptions** - Help AI understand when to use functions
2. **Error handling** - Always return valid JSON, even for errors
3. **Logging** - Log all function calls for debugging
4. **Validation** - Validate parameters before API calls
5. **Caching** - Cache expensive operations
6. **Rate limiting** - Protect against excessive calls
7. **User context** - Always pass username for security
8. **Units** - Always include measurement units in responses

## Security

- ✅ **User isolation**: Each user can only access their own data
- ✅ **Authentication required**: Functions check for valid access token
- ✅ **No data leakage**: Username injected automatically per user
- ✅ **Audit trail**: All function calls logged

## Performance

**Function call costs (tokens):**
- getHomesData: ~500-1000 tokens (one-time setup)
- getHomeStatus: ~200-500 tokens (frequent)
- getMeasure: ~1000-2000 tokens (large datasets)

**Recommendations:**
- Call getHomesData once per conversation
- Cache home/device IDs in conversation
- Limit getMeasure timeframes

---

**Your AI can now intelligently query and interpret your Netatmo weather data!** 🚀

Try asking: "What's the temperature difference between indoor and outdoor right now?"
