package net.pautet.softs.demospring.service;

import com.slack.api.bolt.App;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.model.event.AppMentionEvent;
import com.slack.api.model.event.MessageChannelJoinEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.BinanceOrderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Slack Socket Mode Service
 * <p>
 * Socket Mode allows your bot to connect to Slack via WebSocket instead of HTTP webhooks.
 * This is perfect for local development - no need for ngrok, public URLs, or webhook configuration!
 * <p>
 * Benefits:
 * - No public URL needed
 * - Bot connects TO Slack (Slack doesn't need to reach you)
 * - Instant local testing
 * - Same functionality as HTTP mode
 * <p>
 * For production on Heroku, use HTTP mode (SlackBotController)
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "slack.socket-mode.enabled", havingValue = "true")
public class SlackSocketModeService {

    @Value("${slack.bot-token}")
    private String botToken;

    @Value("${slack.app-token}")
    private String appToken;

    private final SlackBotService slackBotService;
    private final BinanceTradingService tradingService;

    private SocketModeApp socketModeApp;
    private App app;

    public SlackSocketModeService(SlackBotService slackBotService,
                                  BinanceTradingService tradingService) {
        this.slackBotService = slackBotService;
        this.tradingService = tradingService;
    }

    @PostConstruct
    public void init() throws Exception {
        log.debug("🚀 Initializing Slack Socket Mode...");

        // Create Slack App
        app = new App();

        // Register slash command handlers
        registerSlashCommands();

        // Register interactive component handlers
        registerInteractiveHandlers();

        // Register event listeners
        registerEventListeners();

        // Start Socket Mode
        socketModeApp = new SocketModeApp(appToken, app);
        socketModeApp.startAsync();

        log.debug("✅ Slack Socket Mode connected! Bot is ready to receive commands.");
    }

    @PreDestroy
    public void shutdown() throws Exception {
        if (socketModeApp != null) {
            log.debug("🛑 Shutting down Slack Socket Mode...");
            socketModeApp.stop();
            log.debug("✅ Slack Socket Mode stopped");
        }
    }

    /**
     * Register all slash command handlers
     */
    private void registerSlashCommands() {
        log.debug("📝 Registering slash commands...");

        // Main /eth command
        app.command("/eth", (req, ctx) -> {
            String text = req.getPayload().getText();
            String userId = req.getPayload().getUserId();
            String channelId = req.getPayload().getChannelId();

            log.info("Received /eth command: text='{}' from user={}", text, userId);

            // Acknowledge immediately
            ctx.ack();

            // Process in background
            processCommandAsync(text, userId, channelId);

            return ctx.ack();
        });

        log.debug("✅ Slash commands registered: /eth");
    }

    /**
     * Register interactive component handlers (buttons, etc.)
     */
    private void registerInteractiveHandlers() {
        log.debug("📝 Registering interactive handlers...");

        // Analyze Market button
        app.blockAction("analyze_market", (req, ctx) -> {
            String userId = req.getPayload().getUser().getId();
            String channelId = req.getPayload().getChannel().getId();

            ctx.ack();
            slackBotService.handleAnalyzeCommand(userId, channelId);
            return ctx.ack();
        });

        // Quick Buy button
        app.blockAction("quick_buy", (req, ctx) -> {
            String channelId = req.getPayload().getChannel().getId();

            ctx.ack();
            slackBotService.sendMessage(channelId, "🛒 Quick buy feature coming soon!\nUse `/eth buy $500` for now.");
            return ctx.ack();
        });

        // Quick Sell button
        app.blockAction("quick_sell", (req, ctx) -> {
            String channelId = req.getPayload().getChannel().getId();

            ctx.ack();
            slackBotService.sendMessage(channelId, "💵 Quick sell feature coming soon!\nUse `/eth sell 0.5` for now.");
            return ctx.ack();
        });

        // Refresh Analysis button
        app.blockAction("refresh_analysis", (req, ctx) -> {
            String userId = req.getPayload().getUser().getId();
            String channelId = req.getPayload().getChannel().getId();

            ctx.ack();
            slackBotService.handleAnalyzeCommand(userId, channelId);
            return ctx.ack();
        });

        // Get Details button
        app.blockAction("get_details", (req, ctx) -> {
            String channelId = req.getPayload().getChannel().getId();

            ctx.ack();
            
            String details = """
                📊 *Technical Analysis Details*
                
                Our AI analyzes multiple data sources:
                • 3 timeframes (5m, 15m, 1h)
                • 6+ technical indicators
                • Market sentiment
                • Fear & Greed Index
                • Your portfolio context
                
                For full analysis, use `/eth analyze`
                """;
            
            slackBotService.sendMessage(channelId, details);
            return ctx.ack();
        });

        // Execute Trade button
        app.blockAction("execute_trade", (req, ctx) -> {
            String channelId = req.getPayload().getChannel().getId();

            ctx.ack();
            slackBotService.sendMessage(channelId, 
                "⚠️ Trade execution in Slack coming soon!\n" +
                "For now, use the web app to execute trades.\n" +
                "Or use `/eth buy $500` for preview.");
            return ctx.ack();
        });

        // View Trades button
        app.blockAction("view_trades", (req, ctx) -> {
            String channelId = req.getPayload().getChannel().getId();

            ctx.ack();
            slackBotService.sendMessage(channelId, "📜 Use `/eth portfolio` to see recent trades.");
            return ctx.ack();
        });

        // Refresh Recommendation button
        app.blockAction("refresh_recommendation", (req, ctx) -> {
            String userId = req.getPayload().getUser().getId();
            String channelId = req.getPayload().getChannel().getId();

            ctx.ack();
            slackBotService.handleRecommendCommand(userId, channelId);
            return ctx.ack();
        });

        // View Portfolio button
        app.blockAction("view_portfolio", (req, ctx) -> {
            String userId = req.getPayload().getUser().getId();
            String channelId = req.getPayload().getChannel().getId();

            ctx.ack();
            slackBotService.handlePortfolioCommand(userId, channelId);
            return ctx.ack();
        });

        log.debug("✅ Interactive handlers registered");
    }

    /**
     * Register event listeners (mentions, messages, etc.)
     */
    private void registerEventListeners() {
        log.debug("📝 Registering event listeners...");

        // Bot mentioned in a channel
        app.event(AppMentionEvent.class, (req, ctx) -> {
            String text = req.getEvent().getText();
            String userId = req.getEvent().getUser();
            String channelId = req.getEvent().getChannel();

            log.info("Bot mentioned: '{}' by user={}", text, userId);

            // Remove bot mention from text
            String cleanText = text.replaceAll("<@[A-Z0-9]+>", "").trim();

            // If empty, show help
            if (cleanText.isEmpty()) {
                slackBotService.handleHelpCommand(channelId);
            } else {
                // Process as if it was a slash command
                processCommandAsync(cleanText, userId, channelId);
            }

            return ctx.ack();
        });

        // Bot joins a channel (e.g., when #ethbot is created)
        app.event(MessageChannelJoinEvent.class, (req, ctx) -> {
            String channelId = req.getEvent().getChannel();
            String channelType = req.getEvent().getChannelType();
            log.debug("Bot joined channel: {} (type: {})", channelId, channelType);
            return ctx.ack();
        });

        log.debug("✅ Event listeners registered");
    }

    /**
     * Process command asynchronously to avoid blocking
     */
    private void processCommandAsync(String text, String userId, String channelId) {
        new Thread(() -> {
            try {
                processCommand(text, userId, channelId);
            } catch (Exception e) {
                log.error("Error processing command", e);
                slackBotService.sendMessage(channelId, "❌ Error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Process slash command text
     */
    private void processCommand(String text, String userId, String channelId) {
        String subCommand = text != null && !text.isEmpty() ? 
                           text.split(" ")[0].toLowerCase() : "help";

        log.info("Processing subcommand: '{}' for user={}", subCommand, userId);

        switch (subCommand) {
            case "recommend", "rec" -> slackBotService.handleRecommendCommand(userId, channelId);
            case "recommendations", "recs", "memory" -> slackBotService.handleRecommendationsHistoryCommand(userId, channelId, text);
            case "analyze", "analysis" -> slackBotService.handleAnalyzeCommand(userId, channelId);
            case "price" -> slackBotService.handlePriceCommand(userId, channelId);
            case "portfolio", "balance" -> slackBotService.handlePortfolioCommand(userId, channelId);
            case "trades", "history" -> handleTradesCommand(userId, channelId);
            case "buy" -> handleBuyCommand(channelId, text);
            case "sell" -> handleSellCommand(channelId, text);
            case "reset" -> slackBotService.handleResetCommand(userId, channelId);
            case "context", "debug" -> slackBotService.handleContextCommand(userId, channelId);
            case "help" -> slackBotService.handleHelpCommand(channelId);
            default -> slackBotService.sendMessage(channelId,
                    "❓ Unknown command: `" + subCommand + "`\nTry `/eth help` for available commands.");
        }
    }

    /**
     * Handle trades command - Full trade history with details
     */
    private void handleTradesCommand(String userId, String channelId) {
        slackBotService.handleTradesCommand(userId, channelId);
    }

    /**
     * Handle buy command - Now uses Testnet or Paper Trading
     */
    private void handleBuyCommand(String channelId, String text) {
        try {
            String[] parts = text.split(" ");
            if (parts.length < 2) {
                slackBotService.sendMessage(channelId, 
                    "❌ Usage: `/eth buy <amount>`\n" +
                    "Example: `/eth buy $500`");
                return;
            }

            String amountStr = parts[1].replace("$", "").replace(",", "");
            BigDecimal usdtAmount = new BigDecimal(amountStr);

            if (usdtAmount.compareTo(BigDecimal.ZERO) <= 0) {
                slackBotService.sendMessage(channelId, "❌ Amount must be positive");
                return;
            }

            slackBotService.sendMessage(channelId, "⏳ Placing BUY order...");

            // Execute via unified TradingService (testnet)
            BinanceOrderResponse order = tradingService.buyETH(usdtAmount);

            slackBotService.sendMessage(channelId, String.format("""
                    ✅ BUY Order Executed 🧪 TESTNET
                    
                    Bought: %.6f ETH
                    Spent: $%.2f
                    Average Price: $%.2f
                    Order ID: %s
                    
                    Use `/eth portfolio` to view balance
                    """,
                    order.executedQty(),
                    usdtAmount,
                    order.getAveragePrice(),
                    order.orderId()
            ));

        } catch (IllegalArgumentException e) {
            slackBotService.sendMessage(channelId, 
                "❌ " + e.getMessage());
        } catch (Exception e) {
            log.error("Error executing buy", e);
            slackBotService.sendMessage(channelId, 
                "❌ Error: " + e.getMessage() + "\nUsage: `/eth buy $500`");
        }
    }

    /**
     * Handle sell command - Now uses Testnet or Paper Trading
     */
    private void handleSellCommand(String channelId, String text) {
        try {
            String[] parts = text.split(" ");
            if (parts.length < 2) {
                slackBotService.sendMessage(channelId, 
                    "❌ Usage: `/eth sell <amount>`\n" +
                    "Example: `/eth sell 0.5`");
                return;
            }

            BigDecimal ethAmount = new BigDecimal(parts[1]);

            if (ethAmount.compareTo(BigDecimal.ZERO) <= 0) {
                slackBotService.sendMessage(channelId, "❌ Amount must be positive");
                return;
            }

            slackBotService.sendMessage(channelId, "⏳ Placing SELL order...");

            // Execute via unified TradingService (testnet)
            BinanceOrderResponse order = tradingService.sellETH(ethAmount);

            slackBotService.sendMessage(channelId, String.format("""
                    ✅ SELL Order Executed 🧪 TESTNET
                    
                    Sold: %.6f ETH
                    Received: $%.2f
                    Average Price: $%.2f
                    Order ID: %s
                    
                    Use `/eth portfolio` to view balance
                    """,
                    order.executedQty(),
                    order.cummulativeQuoteQty(),
                    order.getAveragePrice(),
                    order.orderId()
            ));

        } catch (IllegalArgumentException e) {
            slackBotService.sendMessage(channelId, 
                "❌ " + e.getMessage());
        } catch (Exception e) {
            log.error("Error executing sell", e);
            slackBotService.sendMessage(channelId, 
                "❌ Error: " + e.getMessage() + "\nUsage: `/eth sell 0.5`");
        }
    }
}
