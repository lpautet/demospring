# ⚡ 5-Minute Quick Start - Socket Mode

## ✅ The Easiest Way to Test Your Slack Bot Locally!

**No webhooks. No ngrok. No hassle.** Just 5 simple steps.

---

## 📋 Prerequisites

- [ ] Slack workspace (you have admin access)
- [ ] Redis running (`redis-server`)
- [ ] OpenAI API key

---

## 🚀 Quick Start Steps

### Step 1: Slack App Setup (2 minutes)

**A. Go to Slack API:**
```
https://api.slack.com/apps
```

**B. Create or select app:**
- If new: Click "Create New App" → "From scratch"
  - Name: `ETH Trading Bot`
  - Choose your workspace

**C. Enable Socket Mode:**
1. Left sidebar → "Socket Mode"
2. Toggle to **ON**
3. Click "Generate an app-level token"
   - Name: `socket-token`
   - Add scope: `connections:write`
   - Click "Generate"
   - **📋 Copy token** (starts with `xapp-1-...`)
   - Save it somewhere!

**D. Add Bot Permissions:**
1. Left sidebar → "OAuth & Permissions"
2. Scroll to "Bot Token Scopes"
3. Add these scopes:
   - `commands`
   - `chat:write`
   - `im:write`
   - `users:read`

**E. Install App:**
1. Scroll to top of "OAuth & Permissions"
2. Click "Install to Workspace"
3. Click "Allow"
4. **📋 Copy "Bot User OAuth Token"** (starts with `xoxb-...`)

---

### Step 2: Configure Your App (1 minute)

**Copy the example env file:**
```bash
cd /Users/lpautet/playground/demospring
cp .env.example .env
```

**Edit `.env` and add your tokens:**
```bash
# Required
SLACK_BOT_TOKEN=xoxb-YOUR-TOKEN-HERE
SLACK_APP_TOKEN=xapp-1-YOUR-TOKEN-HERE
SLACK_SOCKET_MODE=true
SLACK_ENABLED=true

# Required
REDIS_URL=redis://localhost:6379
OPENAI_API_KEY=sk-YOUR-KEY-HERE

# Optional (can leave empty for testing)
BINANCE_TESTNET=true
JWT_SECRET=any-random-string-here
```

---

### Step 3: Start Redis (30 seconds)

**Open a terminal:**
```bash
redis-server
```

**Leave it running!** You should see:
```
Ready to accept connections
```

---

### Step 4: Run Your App (1 minute)

**Open another terminal:**
```bash
cd /Users/lpautet/playground/demospring
./mvnw spring-boot:run
```

**Wait for this message:**
```
✅ Slack Socket Mode connected! Bot is ready to receive commands.
💡 Try: /eth help in any Slack channel
```

**🎉 Your bot is running!**

---

### Step 5: Test in Slack (30 seconds)

**Open Slack and type:**
```
/eth help
```

**You should see:**
```
🤖 ETH Trading Bot Commands

📊 ANALYSIS
/eth analyze - Full market analysis with AI
/eth price - Quick price check

💼 PORTFOLIO
/eth portfolio - View portfolio & stats

💰 TRADING
/eth buy <amount> - Buy ETH
/eth sell <amount> - Sell ETH

ℹ️ Need help? Just type /eth help
```

**Try more commands:**
```
/eth price
/eth analyze
/eth portfolio
```

**🎊 It works! You're done!**

---

## 🎯 What Just Happened?

```
Your Spring Boot App (localhost)
    ↓
Opens WebSocket connection
    ↓
Connects TO Slack
    ↓
Slack sends commands over WebSocket
    ↓
Your app processes them
    ↓
Sends responses back
    ↓
You see results in Slack
```

**No public URL needed!** Your app connects TO Slack, not the other way around.

---

## 💡 Quick Tips

### Making Changes

1. **Edit code** (e.g., change a message in `SlackBotService.java`)
2. **Stop app** (Ctrl+C in terminal)
3. **Restart app** (`./mvnw spring-boot:run`)
4. **Test immediately** in Slack

**No git push, no deploy, no webhook updates!**

### Testing Commands

**Quick check:**
```
/eth price  → 1 second response
```

**Full analysis:**
```
/eth analyze  → 5-10 seconds (AI thinking)
```

**Portfolio:**
```
/eth portfolio  → Your $10k paper trading account
```

### Troubleshooting

**Command not found `/eth`:**
- Check: Did you enable Socket Mode in Slack?
- Check: Is `SLACK_SOCKET_MODE=true` in `.env`?

**Bot not responding:**
- Check: Is app running? (See "✅ Slack Socket Mode connected!" in logs)
- Check: Are tokens correct in `.env`?
- Check: Is Redis running?

**WebSocket connection failed:**
- Check: App-level token starts with `xapp-1-...`
- Check: Token has `connections:write` scope
- Restart app

---

## 🎨 What You Can Do Now

### Interact with Your Bot

**In any channel:**
```
/eth price
/eth analyze
/eth portfolio
```

**Click buttons** in responses:
- [Analyze Market] - Full analysis
- [Buy] / [Sell] - Quick actions
- [Refresh] - Update data

**Mention the bot:**
```
@ETH Trading Bot what's the price?
```

### Develop Features

**Change button handlers:**
```java
// In SlackSocketModeService.java
app.blockAction("analyze_market", (req, ctx) -> {
    // Your custom logic here
    slackBotService.sendMessage(channelId, "Custom message!");
    return ctx.ack();
});
```

**Add new commands:**
```java
// In processCommand() method
case "newcmd" -> handleNewCommand(userId, channelId);
```

**Test immediately** - just restart app!

---

## 📚 More Information

**Full Socket Mode Guide:**
- Read: `SLACK_SOCKET_MODE.md`
- 600+ lines of detailed documentation
- Troubleshooting, best practices, production deployment

**Setup Comparison:**
- `SLACK_BOT_SETUP.md` - HTTP mode (for production)
- `SLACK_SOCKET_MODE.md` - Socket mode (for local dev)

**Implementation Details:**
- `SlackSocketModeService.java` - Socket Mode service
- `SlackBotService.java` - Business logic (shared)
- `SlackBotController.java` - HTTP mode (for Heroku)

---

## 🔄 When Ready for Production

**Socket Mode is great for local dev, but for Heroku use HTTP mode:**

```bash
# 1. Deploy to Heroku
git push heroku main

# 2. Configure webhooks in Slack app settings
# - Slash Commands: https://your-app.herokuapp.com/slack/commands
# - Interactivity: https://your-app.herokuapp.com/slack/actions

# 3. Disable Socket Mode
heroku config:set SLACK_SOCKET_MODE=false
heroku config:set SLACK_BOT_TOKEN=xoxb-your-token
```

**Both modes use the same `SlackBotService.java`, so your bot works identically!**

---

## ✅ Checklist Summary

- [ ] Created/selected Slack app
- [ ] Enabled Socket Mode in Slack
- [ ] Generated app-level token (`xapp-...`)
- [ ] Added bot scopes
- [ ] Installed app and got bot token (`xoxb-...`)
- [ ] Created `.env` with both tokens
- [ ] Started Redis
- [ ] Ran Spring Boot app
- [ ] Saw "✅ Slack Socket Mode connected!" message
- [ ] Tested `/eth help` in Slack
- [ ] Bot responded!

**🎉 Congratulations! Your Slack bot is working locally!**

---

## 🚀 Next Steps

**Today:**
- [ ] Try all commands (`/eth price`, `/eth analyze`, `/eth portfolio`)
- [ ] Click buttons to test interactivity
- [ ] Check your portfolio

**This Week:**
- [ ] Make code changes and test
- [ ] Customize messages
- [ ] Add features

**When Ready:**
- [ ] Deploy to Heroku
- [ ] Switch to HTTP mode
- [ ] Share with team

---

**Need Help?**
- Check logs in your terminal
- Read `SLACK_SOCKET_MODE.md` for troubleshooting
- Make sure Redis and OpenAI keys are configured

**Happy Trading! 📈🤖**
