# 🚀 Slack Socket Mode - Super Easy Local Development!

## ✅ What is Socket Mode?

**Socket Mode** lets your bot connect TO Slack via WebSocket instead of Slack calling YOUR webhooks.

### Traditional HTTP Mode (Production)
```
Slack → HTTP POST → Your Public URL (ngrok/Heroku) → Your App
```
❌ Needs public URL  
❌ Needs webhook setup  
❌ Needs ngrok for local dev  

### Socket Mode (Local Dev) ⭐
```
Your App → WebSocket → Slack
```
✅ **No public URL needed!**  
✅ **No webhook configuration!**  
✅ **No ngrok!**  
✅ **Bot connects to Slack, not the other way around**  

---

## 🎯 When to Use What?

| Environment | Mode | Why |
|------------|------|-----|
| **Local Dev** | Socket Mode ✅ | No webhooks, instant testing |
| **Heroku/Production** | HTTP Mode ✅ | More scalable, standard approach |

---

## ⚡ Quick Start - Socket Mode (5 Minutes!)

### Step 1: Enable Socket Mode in Slack (2 min)

1. **Go to:** https://api.slack.com/apps
2. **Select or create your app:** `ETH Trading Bot`
3. **In sidebar, click:** "Socket Mode"
4. **Toggle ON:** "Enable Socket Mode"
5. **Click:** "Generate an app-level token"
   - Token Name: `socket-token`
   - Scopes: `connections:write`
   - Click "Generate"
   - **Copy the token** (starts with `xapp-...`)
   - Click "Done"

6. **In sidebar, click:** "OAuth & Permissions"
   - If not already done, add scopes: `commands`, `chat:write`, `im:write`, `users:read`
   - Install to workspace if needed
   - **Copy Bot User OAuth Token** (starts with `xoxb-...`)

### Step 2: Configure Your App (1 min)

**Create `.env` file** in project root:
```bash
# Slack Configuration for Socket Mode
SLACK_BOT_TOKEN=xoxb-your-bot-token-here
SLACK_APP_TOKEN=xapp-1-your-app-token-here
SLACK_SOCKET_MODE=true

# Other required env vars
REDIS_URL=redis://localhost:6379
OPENAI_API_KEY=sk-your-key
BINANCE_TESTNET=true
```

### Step 3: Run Your App (1 min)

```bash
cd /Users/lpautet/playground/demospring

# Make sure Redis is running
redis-server

# In another terminal, start your app
./mvnw spring-boot:run
```

**You should see:**
```
🚀 Initializing Slack Socket Mode...
📝 Registering slash commands...
✅ Slash commands registered: /eth
📝 Registering interactive handlers...
✅ Interactive handlers registered
📝 Registering event listeners...
✅ Event listeners registered
✅ Slack Socket Mode connected! Bot is ready to receive commands.
💡 Try: /eth help in any Slack channel
```

### Step 4: Test It! (1 min)

**In Slack:**
```
/eth help
/eth price
/eth analyze
```

**That's it!** 🎉 No webhooks, no ngrok, no hassle!

---

## 📋 Complete Setup Comparison

### Socket Mode Setup (Local Dev)

```bash
# 1. Enable Socket Mode in Slack app settings
# 2. Generate app-level token (xapp-...)
# 3. Add to .env:
SLACK_BOT_TOKEN=xoxb-...
SLACK_APP_TOKEN=xapp-...
SLACK_SOCKET_MODE=true

# 4. Run app
./mvnw spring-boot:run

# Done! ✅
```

**No need for:**
- ❌ Public URL
- ❌ Slash command configuration (Request URL)
- ❌ Interactivity configuration (Request URL)
- ❌ ngrok
- ❌ Port forwarding

### HTTP Mode Setup (Production/Heroku)

```bash
# 1. Deploy to Heroku
git push heroku main

# 2. Configure Slack app:
# - Slash Commands → Request URL: https://your-app.herokuapp.com/slack/commands
# - Interactivity → Request URL: https://your-app.herokuapp.com/slack/actions

# 3. Set env var
heroku config:set SLACK_BOT_TOKEN=xoxb-...
heroku config:set SLACK_SOCKET_MODE=false

# Done! ✅
```

---

## 🔧 How It Works

### Architecture

```
Your Spring Boot App
    ↓
SlackSocketModeService (@PostConstruct)
    ↓
Creates Slack App (Bolt framework)
    ↓
Registers handlers:
    - /eth slash command
    - Interactive buttons (analyze_market, quick_buy, etc.)
    - Event listeners (app mentions)
    ↓
Starts WebSocket connection to Slack
    ↓
Listens for events from Slack
    ↓
When user types /eth price:
    ↓
Slack sends event over WebSocket
    ↓
SlackSocketModeService receives it
    ↓
Routes to SlackBotService.handlePriceCommand()
    ↓
Sends response back over WebSocket
    ↓
User sees result in Slack
```

### Code Flow

**User types:** `/eth analyze`

1. **Slack** sends event over WebSocket
2. **SlackSocketModeService** receives in `app.command("/eth", ...)`
3. Acknowledges immediately: `ctx.ack()`
4. Spawns background thread: `processCommandAsync()`
5. Routes to: `slackBotService.handleAnalyzeCommand()`
6. AI performs analysis (5-10 seconds)
7. Sends result: `slackBotService.sendMessage()`
8. **User** sees analysis in Slack

---

## 🎛️ Configuration Options

### Environment Variables

```bash
# Required for Socket Mode
SLACK_BOT_TOKEN=xoxb-...           # Bot OAuth token
SLACK_APP_TOKEN=xapp-...           # App-level token (Socket Mode only)
SLACK_SOCKET_MODE=true             # Enable Socket Mode

# Optional
SLACK_ENABLED=true                 # Master switch
SLACK_CHANNEL_ID=C12345            # Default channel (optional)
```

### application.properties

```properties
# Socket Mode (for local dev)
slack.socket-mode.enabled=${SLACK_SOCKET_MODE:false}

# Standard config (both modes)
slack.bot-token=${SLACK_BOT_TOKEN:}
slack.app-token=${SLACK_APP_TOKEN:}
slack.enabled=${SLACK_ENABLED:false}
```

### Spring Profile Setup (Optional)

**application-dev.properties** (local):
```properties
slack.socket-mode.enabled=true
```

**application-prod.properties** (Heroku):
```properties
slack.socket-mode.enabled=false
```

**Run with:**
```bash
# Local
./mvnw spring-boot:run -Dspring.profiles.active=dev

# Heroku (set in config)
heroku config:set SPRING_PROFILES_ACTIVE=prod
```

---

## 🔄 Switching Between Modes

### Local Development → Production

**Before deploying to Heroku:**

1. **Disable Socket Mode:**
   ```bash
   heroku config:set SLACK_SOCKET_MODE=false
   ```

2. **Configure webhooks in Slack app:**
   - Slash Commands → Request URL: `https://your-app.herokuapp.com/slack/commands`
   - Interactivity → Request URL: `https://your-app.herokuapp.com/slack/actions`

3. **Deploy:**
   ```bash
   git push heroku main
   ```

4. **Remove app token** (not needed in HTTP mode):
   ```bash
   heroku config:unset SLACK_APP_TOKEN
   ```

### Production → Local Development

1. **Enable Socket Mode:**
   ```bash
   # In .env file
   SLACK_SOCKET_MODE=true
   SLACK_APP_TOKEN=xapp-your-token
   ```

2. **Run locally:**
   ```bash
   ./mvnw spring-boot:run
   ```

---

## 🎯 Supported Features

All features work in **both modes**:

### Slash Commands
- ✅ `/eth analyze` - Full analysis
- ✅ `/eth price` - Quick price
- ✅ `/eth portfolio` - Portfolio status
- ✅ `/eth help` - Help menu
- ✅ `/eth buy <amount>` - Buy preview
- ✅ `/eth sell <amount>` - Sell preview
- ✅ `/eth trades` - Trade history

### Interactive Components
- ✅ Buttons (Analyze Market, Buy, Sell, etc.)
- ✅ Block Kit formatted messages
- ✅ Rich UI with emojis and layouts

### Events
- ✅ App mentions (@ETH Trading Bot help)
- ✅ Direct messages (in Socket Mode)

---

## 🐛 Troubleshooting

### "Bot not responding" in Socket Mode

**Check 1: Is Socket Mode enabled in Slack?**
```
Go to: https://api.slack.com/apps → Your App → Socket Mode
Should be: ON ✅
```

**Check 2: Is app-level token generated?**
```
Socket Mode → App-Level Tokens
Should have token with connections:write scope
```

**Check 3: Is environment variable set?**
```bash
echo $SLACK_APP_TOKEN
# Should output: xapp-1-...

echo $SLACK_SOCKET_MODE
# Should output: true
```

**Check 4: Check logs**
```
Should see:
✅ Slack Socket Mode connected! Bot is ready to receive commands.

If not, check for errors about tokens or WebSocket connection
```

### "Connection refused" or WebSocket errors

**Problem:** Firewall or proxy blocking WebSocket
**Fix:** 
- Check firewall settings
- Try different network
- Make sure ports 443 and 80 are open

### Bot responds slowly

**This is normal!** Socket Mode has slightly higher latency than HTTP mode:
- HTTP Mode: ~100-300ms
- Socket Mode: ~300-500ms
- AI analysis: 3-10 seconds (same in both modes)

### Multiple instances running

**Problem:** If you restart your app, old WebSocket might still be connected

**Fix:**
```bash
# Kill all Java processes
killall java

# Or restart your terminal/IDE

# Then start fresh
./mvnw spring-boot:run
```

---

## 💡 Pro Tips

### Tip 1: Use Socket Mode for Development

**Always** use Socket Mode when developing locally:
```bash
# .env for local dev
SLACK_SOCKET_MODE=true
```

**Switch to HTTP mode only for production:**
```bash
# Heroku config
SLACK_SOCKET_MODE=false
```

### Tip 2: Test Button Interactions

Socket Mode makes it **super easy** to test buttons:
1. Change button handler code
2. Restart app (Ctrl+C, then `./mvnw spring-boot:run`)
3. Click button in Slack
4. See changes instantly!

No need to redeploy to Heroku!

### Tip 3: Debug with Logs

Socket Mode logs are very helpful:
```java
log.info("Received /eth command: text='{}' from user={}", text, userId);
```

You see everything in your terminal in real-time.

### Tip 4: Parallel Development

With Socket Mode, multiple developers can work simultaneously:
- Each developer runs their own local instance
- Each connects via their own Socket
- No port conflicts!
- No webhook URL conflicts!

### Tip 5: Rapid Iteration

**Development cycle:**
1. Make code change
2. Restart app (5 seconds)
3. Test in Slack immediately
4. Repeat

**No need for:**
- Git commit
- Git push
- Heroku build (5 minutes)
- Webhook updates

---

## 📊 Performance Comparison

| Aspect | Socket Mode | HTTP Mode |
|--------|------------|-----------|
| **Setup Time** | 5 minutes | 15 minutes |
| **Local Dev** | Perfect ✅ | Needs ngrok ❌ |
| **Response Time** | ~300ms | ~100ms |
| **Scalability** | Limited | Excellent ✅ |
| **Production** | OK | Perfect ✅ |
| **Debugging** | Easy ✅ | Harder |
| **Multi-Instance** | One per app | Load balanced ✅ |

**Recommendation:**
- 🏠 **Local Dev:** Socket Mode
- 🚀 **Production:** HTTP Mode

---

## 🚀 Full Example Setup

### Complete Local Dev Setup

```bash
# 1. Clone and setup
cd /Users/lpautet/playground/demospring

# 2. Create .env
cat > .env << EOF
SLACK_BOT_TOKEN=xoxb-your-token
SLACK_APP_TOKEN=xapp-1-your-app-token
SLACK_SOCKET_MODE=true
SLACK_ENABLED=true

REDIS_URL=redis://localhost:6379
OPENAI_API_KEY=sk-your-key
BINANCE_TESTNET=true
BINANCE_API_KEY=
BINANCE_API_SECRET=

JWT_SECRET=your-jwt-secret
REDIRECT_URI=http://localhost:8080
EOF

# 3. Start Redis
redis-server &

# 4. Run app
./mvnw spring-boot:run

# 5. Test in Slack
# Open Slack and type: /eth help
```

### Production Heroku Setup

```bash
# 1. Deploy code
git add .
git commit -m "Add Slack Socket Mode support"
git push heroku main

# 2. Configure for HTTP mode
heroku config:set SLACK_SOCKET_MODE=false
heroku config:set SLACK_BOT_TOKEN=xoxb-your-token

# 3. Configure webhooks in Slack app
# Slash Commands URL: https://your-app.herokuapp.com/slack/commands
# Interactivity URL: https://your-app.herokuapp.com/slack/actions

# 4. Test in Slack
# Type: /eth help
```

---

## 🎉 Summary

### Socket Mode Benefits

**For Local Development:**
✅ **No webhooks** - Bot connects TO Slack  
✅ **No ngrok** - No public URL needed  
✅ **Instant testing** - Change code, restart, test  
✅ **Easy debugging** - See everything in terminal  
✅ **5-minute setup** - Faster than HTTP mode  

**Setup is literally:**
1. Enable Socket Mode in Slack (1 min)
2. Generate app-level token (1 min)
3. Add to `.env` (1 min)
4. Run app (1 min)
5. Test in Slack (1 min)

### What You Built

✅ **SlackSocketModeService.java** (300+ lines)  
✅ **WebSocket connection handling**  
✅ **All slash commands working**  
✅ **All interactive buttons working**  
✅ **Event listeners (mentions)**  
✅ **Background processing**  
✅ **Full integration with existing services**  

### Quick Reference

**Enable Socket Mode:**
```bash
SLACK_SOCKET_MODE=true
```

**Disable Socket Mode:**
```bash
SLACK_SOCKET_MODE=false
```

**Check if running:**
```bash
# Should see in logs:
✅ Slack Socket Mode connected!
```

---

## 🎓 Next Steps

**Today:**
1. ✅ Enable Socket Mode in Slack app
2. ✅ Get app-level token
3. ✅ Add to `.env`
4. ✅ Run and test locally

**This Week:**
1. Develop features locally with Socket Mode
2. Test thoroughly
3. Deploy to Heroku with HTTP mode

**Enjoy hassle-free local development!** 🎊

No more ngrok, no more webhook headaches! 🎉
