# Slack Bot Integration Setup

This application now supports publishing log messages to a Slack channel automatically using a Slack bot.

## Prerequisites

You need to create a Slack App and obtain the necessary credentials:

1. **Create a Slack App**
   - Go to https://api.slack.com/apps
   - Click "Create New App" → "From scratch"
   - Give it a name (e.g., "DemoSpring Logger Bot") and select your workspace

2. **Configure Bot Token Scopes**
   - Navigate to "OAuth & Permissions" in the sidebar
   - Under "Bot Token Scopes", add the following scopes:
     - `chat:write` - Post messages to channels
     - `chat:write.public` - Post to public channels without joining
     - `channels:read` - View basic channel information (for channel lookup)
     - `channels:manage` - Create and manage channels (for auto-channel creation)
   
3. **Install App to Workspace**
   - Scroll up to "OAuth Tokens for Your Workspace"
   - Click "Install to Workspace"
   - Authorize the app
   - Copy the "Bot User OAuth Token" (starts with `xoxb-`)

4. **Optional: Get Channel ID** (or use channel name - see Configuration below)
   - Open Slack and navigate to the channel where you want logs posted
   - Right-click the channel name → "View channel details"
   - Scroll down to find the Channel ID (e.g., `C01234ABCDE`)

5. **Optional: Invite Bot to Channel** (if using a private channel)
   - In the channel, type: `/invite @YourBotName`
   - Note: If using auto-channel creation, the bot will create a new public channel

## Configuration

You have three options for configuring the Slack channel:

### Option 1: Use Channel Name (Recommended - Easiest)

The bot will automatically find the channel by name and even create it if it doesn't exist:

```bash
export SLACK_BOT_TOKEN="xoxb-your-bot-token-here"
export SLACK_CHANNEL_NAME="app-logs"  # Can use "#app-logs" or "app-logs"
export SLACK_ENABLED="true"
export SLACK_AUTO_CREATE_CHANNEL="true"  # Default is true
```

### Option 2: Use Channel ID (More Direct)

If you already know the channel ID:

```bash
export SLACK_BOT_TOKEN="xoxb-your-bot-token-here"
export SLACK_CHANNEL_ID="C01234ABCDE"
export SLACK_ENABLED="true"
```

### Option 3: Use Channel Name Without Auto-Create

Find the channel by name but don't create it if missing:

```bash
export SLACK_BOT_TOKEN="xoxb-your-bot-token-here"
export SLACK_CHANNEL_NAME="app-logs"
export SLACK_ENABLED="true"
export SLACK_AUTO_CREATE_CHANNEL="false"
```

### Local Development (.env or run configuration)

```properties
SLACK_BOT_TOKEN=xoxb-your-bot-token-here
SLACK_CHANNEL_NAME=app-logs
SLACK_ENABLED=true
SLACK_AUTO_CREATE_CHANNEL=true
```

### Disabling Slack Integration

To disable Slack integration (useful for local development):

```bash
export SLACK_ENABLED="false"
```

Or simply don't set the environment variables - the integration will be disabled by default.

## Features

### Automatic Log Publishing

All logs created via `MessageService.info()` and `MessageService.error()` are automatically published to Slack:

```java
@Autowired
private MessageService messageService;

// This will be saved to DB and sent to Slack
messageService.info("Application started successfully");
messageService.error("Failed to connect to external API");
```

### Direct Slack Messages

You can also send messages directly to Slack using `SlackService`:

```java
@Autowired
private SlackService slackService;

// Send a single log message
LogMessage log = new LogMessage("Custom message", "INFO", "manual");
slackService.sendLogMessage(log);

// Send a batch of log messages
List<LogMessage> logs = messageService.getAllMessages();
slackService.sendBatchLogMessages(logs);

// Send a simple text message
slackService.sendSimpleMessage("Deployment completed! 🚀");
```

## Message Format

Messages sent to Slack include:
- **Emoji indicator** based on severity (🔴 ERROR, ⚠️ WARNING, ℹ️ INFO)
- **Severity level** in bold
- **Message content**
- **Source** (frontend/server/manual)
- **Timestamp** in your local timezone

Example:
```
ℹ️ [INFO] Netatmo Token refreshed.
Source: server | Time: 2025-10-28 16:45:30 CET
```

## Severity Levels

The bot automatically assigns emojis based on severity:

- `ERROR` → 🔴
- `WARN` / `WARNING` → ⚠️
- `INFO` → ℹ️
- `DEBUG` → 🐛
- `SUCCESS` → ✅
- Default → 📝

## Channel Name Normalization

When using `SLACK_CHANNEL_NAME`, the bot automatically normalizes channel names:
- Removes `#` prefix if present (e.g., `#app-logs` → `app-logs`)
- Converts to lowercase
- Replaces spaces and underscores with hyphens (e.g., `App Logs` → `app-logs`)

This means you can use `"App Logs"`, `"app_logs"`, `"#app-logs"`, or `"app-logs"` - they all resolve to the same channel.

## Startup Behavior

On application startup, the bot will:
1. Check if Slack is enabled and bot token is configured
2. Try to resolve channel:
   - If `SLACK_CHANNEL_ID` is set, use it directly
   - Otherwise, search for channel by `SLACK_CHANNEL_NAME`
   - If not found and `SLACK_AUTO_CREATE_CHANNEL=true`, create the channel
3. Log the resolved channel ID and confirmation message

Check your application logs for messages like:
```
Slack bot integration initialized successfully. Logs will be sent to channel: C01234ABCDE
```

## Troubleshooting

### Messages not appearing in Slack

1. **Check if Slack is enabled**: Verify `SLACK_ENABLED=true`
2. **Verify bot token**: Ensure the token is correct and starts with `xoxb-`
3. **Check channel configuration**: Set either `SLACK_CHANNEL_ID` or `SLACK_CHANNEL_NAME`
4. **Bot permissions**: Verify the bot has all required scopes:
   - `chat:write` for posting messages
   - `channels:read` for finding channels by name
   - `channels:manage` for creating channels (if using auto-create)
5. **Private channel**: Invite the bot to the channel with `/invite @BotName`
6. **Application logs**: Check for initialization errors during startup
7. **Channel creation failed**: If auto-create fails, check bot has `channels:manage` scope

### Channel not found or created

- Check the normalized channel name in logs
- Verify `channels:read` and `channels:manage` scopes are granted
- Try using `SLACK_CHANNEL_ID` directly instead

### Testing the integration

You can test by calling:
```java
messageService.info("Testing Slack integration!");
```

Check your application logs for any Slack-related errors.

## Security Best Practices

- **Never commit tokens**: Keep `SLACK_BOT_TOKEN` in environment variables or secrets management
- **Use private channels**: Consider using private channels for sensitive logs
- **Rotate tokens**: Regularly rotate your bot tokens from the Slack API dashboard
- **Limit scopes**: Only grant the minimum required OAuth scopes

## Dependencies

This integration uses the official Slack Java SDK:
- `com.slack.api:slack-api-client:1.45.0`

The dependency is already added to `pom.xml`.
