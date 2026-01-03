package net.pautet.softs.demospring.rest;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.service.SlackBotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Slack Bot Controller (HTTP Mode)
 * Handles slash commands and interactive actions from Slack via HTTP webhooks
 * 
 * NOTE: This is DISABLED in production. We use SlackSocketModeService instead.
 * Only activates if socket-mode is disabled (for migration purposes).
 */
@Slf4j
@RestController
@RequestMapping("/slack")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "slack.socket-mode.enabled", 
    havingValue = "false", 
    matchIfMissing = false
)
public class SlackBotController {

    private final SlackBotService slackBotService;

    public SlackBotController(SlackBotService slackBotService) {
        this.slackBotService = slackBotService;
    }

    /**
     * Handle slash commands from Slack
     * Endpoint: /slack/commands
     * Called when user types: /eth <command>
     */
    @PostMapping("/commands")
    public ResponseEntity<String> handleSlashCommand(@RequestParam Map<String, String> params) {
        String command = params.get("command");
        String text = params.get("text");
        String userId = params.get("user_id");
        String channelId = params.get("channel_id");
        String responseUrl = params.get("response_url");

        log.info("Received Slack command: {} with text: {} from user: {}", command, text, userId);

        // Acknowledge immediately (Slack expects response within 3 seconds)
        // Process in background
        new Thread(() -> processCommand(command, text, userId, channelId, responseUrl)).start();

        return ResponseEntity.ok("Processing your request...");
    }

    /**
     * Process Slack command (runs in background)
     */
    private void processCommand(String command, String text, String userId, String channelId, String responseUrl) {
        try {
            // All commands start with /eth
            if (!"/eth".equals(command)) {
                slackBotService.sendMessage(channelId, "Unknown command: " + command);
                return;
            }

            // Parse subcommand
            String subCommand = text != null && !text.isEmpty() ? text.split(" ")[0].toLowerCase() : "help";

            switch (subCommand) {
                case "recommend", "rec" -> slackBotService.handleRecommendCommand(userId, channelId);
                case "analyze", "analysis" -> slackBotService.handleAnalyzeCommand(userId, channelId);
                case "price" -> slackBotService.handlePriceCommand(userId, channelId);
                case "portfolio", "balance" -> slackBotService.handlePortfolioCommand(userId, channelId);
                case "trades", "history" -> handleTradesCommand(userId, channelId);
                case "buy" -> handleBuyCommand(userId, channelId, text);
                case "sell" -> handleSellCommand(userId, channelId, text);
                case "help" -> slackBotService.handleHelpCommand(channelId);
                default -> slackBotService.sendMessage(channelId,
                        "Unknown command: " + subCommand + "\nTry `/eth help` for available commands.");
            }
        } catch (Exception e) {
            log.error("Error processing command", e);
            slackBotService.sendMessage(channelId, "❌ Error: " + e.getMessage());
        }
    }

    /**
     * Handle interactive actions (button clicks)
     * Endpoint: /slack/actions
     */
    @PostMapping("/actions")
    public ResponseEntity<String> handleInteractiveAction(@RequestParam("payload") String payload) {
        try {
            log.info("Received Slack action: {}", payload);

            // Parse payload (JSON)
            // Handle button clicks:
            // - execute_trade
            // - get_details
            // - refresh_analysis
            // - analyze_market
            // - quick_buy
            // - quick_sell
            // - view_trades

            // For now, acknowledge
            return ResponseEntity.ok("Action received");

        } catch (Exception e) {
            log.error("Error handling interactive action", e);
            return ResponseEntity.ok("Error processing action");
        }
    }

    /**
     * Handle /eth trades command
     */
    private void handleTradesCommand(String userId, String channelId) {
        // Implementation similar to portfolio but showing more trades
        slackBotService.sendMessage(channelId, "📜 Trade history feature coming soon!\nFor now, use `/eth portfolio` to see recent trades.");
    }

    /**
     * Handle /eth buy command
     */
    private void handleBuyCommand(String userId, String channelId, String text) {
        try {
            // Parse amount from text
            // Example: "buy $500" or "buy 500"
            String[] parts = text.split(" ");
            if (parts.length < 2) {
                slackBotService.sendMessage(channelId, "❌ Usage: `/eth buy <amount>`\nExample: `/eth buy $500`");
                return;
            }

            String amountStr = parts[1].replace("$", "");
            double amount = Double.parseDouble(amountStr);

            slackBotService.sendMessage(channelId,
                    String.format("🛒 Buy order preview: $%.2f\n\nFeature coming soon! Use web app for now.", amount));

        } catch (Exception e) {
            slackBotService.sendMessage(channelId, "❌ Invalid amount. Usage: `/eth buy $500`");
        }
    }

    /**
     * Handle /eth sell command
     */
    private void handleSellCommand(String userId, String channelId, String text) {
        try {
            // Parse amount from text
            String[] parts = text.split(" ");
            if (parts.length < 2) {
                slackBotService.sendMessage(channelId, "❌ Usage: `/eth sell <amount>`\nExample: `/eth sell 0.5`");
                return;
            }

            double amount = Double.parseDouble(parts[1]);

            slackBotService.sendMessage(channelId,
                    String.format("💵 Sell order preview: %.4f ETH\n\nFeature coming soon! Use web app for now.", amount));

        } catch (Exception e) {
            slackBotService.sendMessage(channelId, "❌ Invalid amount. Usage: `/eth sell 0.5`");
        }
    }

    /**
     * Health check endpoint for Slack verification
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
