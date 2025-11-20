package net.pautet.softs.demospring.service;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.model.block.*;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.ButtonElement;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.AccountSummary;
import net.pautet.softs.demospring.dto.BinanceTrade;
import net.pautet.softs.demospring.dto.TradeRecommendation;
import net.pautet.softs.demospring.entity.RecommendationHistory;
import net.pautet.softs.demospring.repository.RecommendationHistoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.pautet.softs.demospring.service.BinanceApiService.ETHUSDC;

/**
 * Slack Bot Service for ETH Trading
 * Handles all Slack interactions, message formatting, and bot responses
 */
@Service
@Slf4j
public class SlackBotService {

    private final Slack slack;
    private final MethodsClient methods;
    private final TradingChatService tradingChatService;
    private final QuickRecommendationServiceGrok quickRecommendationService;
    private final BinanceTradingService tradingService;
    private final BinanceApiService binanceApiService;
    private final TradingContextService tradingContextService;
    private final RecommendationHistoryRepository recommendationRepository;

    @Value("${slack.bot-token}")
    private String botToken;

    public SlackBotService(TradingChatService tradingChatService,
                          QuickRecommendationServiceGrok quickRecommendationService,
                          BinanceTradingService tradingService,
                          BinanceApiService binanceApiService,
                          TradingContextService tradingContextService,
                          RecommendationHistoryRepository recommendationRepository) {
        this.slack = Slack.getInstance();
        this.methods = slack.methods();
        this.tradingChatService = tradingChatService;
        this.quickRecommendationService = quickRecommendationService;
        this.tradingService = tradingService;
        this.binanceApiService = binanceApiService;
        this.tradingContextService = tradingContextService;
        this.recommendationRepository = recommendationRepository;
    }

    /**
     * Handle /eth analyze command - Full market analysis
     */
    public void handleAnalyzeCommand(String userId, String channelId) {
        try {
            log.info("Handling /eth analyze for user: {}", userId);

            // Send "thinking" message
            sendMessage(channelId, "🔍 Analyzing market... This may take a few seconds.");

            // Get comprehensive analysis from AI
            String prompt = """
                    Perform a comprehensive market analysis for ETH right now:
                    
                    1. Check current market data (price, 24h change, volume)
                    2. Analyze technical indicators for 5m, 15m, 1h timeframes
                    3. Check market sentiment
                    4. Review portfolio
                    5. Provide trading recommendation
                    
                    Format for Slack: Use clear sections with emojis. Keep it concise but complete.
                    """;

            String analysis = tradingChatService.chat(userId, prompt);

            // Format and send analysis with interactive buttons
            sendAnalysisMessage(channelId, analysis, userId);

        } catch (Exception e) {
            log.error("Error handling analyze command", e);
            sendMessage(channelId, "❌ Error analyzing market: " + e.getMessage());
        }
    }

    /**
     * Handle /eth recommend command - Quick trading recommendation
     * Optimized for frequent evaluations (lower cost, faster)
     */
    public void handleRecommendCommand(String userId, String channelId) {
        try {
            log.info("Handling /eth recommend for user: {}", userId);

            // Send "thinking" message
            sendMessage(channelId, "⚡ Getting quick recommendation...");

            // Get quick recommendation (single AI call, all context pre-loaded)
            net.pautet.softs.demospring.dto.TradeRecommendation recommendation = quickRecommendationService.getQuickRecommendation(userId);

            // Parse and format recommendation
            sendRecommendationMessage(channelId, recommendation, userId);

        } catch (Exception e) {
            log.error("Error handling recommend command", e);
            sendMessage(channelId, "❌ Error getting recommendation: " + e.getMessage());
        }
    }

    /**
     * Handle /eth price command - Quick price check
     */
    public void handlePriceCommand(String userId, String channelId) {
        try {
            log.info("Handling /eth price for user: {}", userId);

            var ticker = binanceApiService.getPrice(ETHUSDC);
            double price = ticker.priceAsDouble();
            
            // Get 24h data
            // Get context for consistent data
            TradingContextService.TradingContext context = tradingContextService.gatherCompleteContext();
            
            double priceChange = context.ticker.priceChange().doubleValue();
            double priceChangePercent = context.ticker.priceChangePercentAsDouble();
            double highPrice = context.ticker.highPrice().doubleValue();
            double lowPrice = context.ticker.lowPrice().doubleValue();

            // Quick technical check from 15m indicators
            double rsi = (double) context.tech15m.get("rsi");
            String rsiSignal = (String) context.tech15m.get("rsiSignal");

            String emoji = priceChangePercent >= 0 ? "🟢" : "🔴";
            String trendEmoji = priceChangePercent > 2 ? "📈" : priceChangePercent < -2 ? "📉" : "➡️";

            List<LayoutBlock> blocks = new ArrayList<>();

            // Header
            blocks.add(HeaderBlock.builder()
                    .text(PlainTextObject.builder().text("📊 ETH/USDC").build())
                    .build());

            // Price info
            blocks.add(SectionBlock.builder()
                    .fields(Arrays.asList(
                            MarkdownTextObject.builder()
                                    .text(String.format("*Price:*\n$%.2f %s", price, emoji))
                                    .build(),
                            MarkdownTextObject.builder()
                                    .text(String.format("*24h Change:*\n%+.2f%% (%+$.2f)", priceChangePercent, priceChange))
                                    .build(),
                            MarkdownTextObject.builder()
                                    .text(String.format("*24h High:*\n$%.2f", highPrice))
                                    .build(),
                            MarkdownTextObject.builder()
                                    .text(String.format("*24h Low:*\n$%.2f", lowPrice))
                                    .build()
                    ))
                    .build());

            blocks.add(DividerBlock.builder().build());

            // Quick technical
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                            .text(String.format("%s *Trend:* %s\n*RSI (15m):* %.1f (%s)",
                                    trendEmoji,
                                    priceChangePercent >= 0 ? "Bullish" : "Bearish",
                                    rsi,
                                    rsiSignal.contains("BUY") ? "Buy signal" : rsiSignal.contains("SELL") ? "Sell signal" : "Neutral"))
                            .build())
                    .build());

            // Action buttons
            blocks.add(ActionsBlock.builder()
                    .elements(Arrays.asList(
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("Analyze Market").build())
                                    .actionId("analyze_market")
                                    .value(userId)
                                    .style("primary")
                                    .build(),
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("Buy").build())
                                    .actionId("quick_buy")
                                    .value(userId)
                                    .build(),
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("Sell").build())
                                    .actionId("quick_sell")
                                    .value(userId)
                                    .build()
                    ))
                    .build());

            sendBlockMessage(channelId, blocks);

        } catch (Exception e) {
            log.error("Error handling price command", e);
            sendMessage(channelId, "❌ Error fetching price: " + e.getMessage());
        }
    }

    /**
     * Handle /eth portfolio command - Portfolio status
     * Shows Binance Testnet account summary
     */
    public void handlePortfolioCommand(String userId, String channelId) {
        try {
            log.info("Handling /eth portfolio for user: {}", userId);

            AccountSummary summary = tradingService.getAccountSummary();
            
            List<BinanceTrade> trades = tradingService.getRecentTrades(10);

            List<LayoutBlock> blocks = new ArrayList<>();

            // Header
            blocks.add(HeaderBlock.builder()
                    .text(PlainTextObject.builder().text("💼 Your Portfolio").build())
                    .build());

            // Mode indicator
            blocks.add(ContextBlock.builder()
                    .elements(List.of(
                            MarkdownTextObject.builder()
                                    .text("🧪 | TESTNET - Binance Testnet (Real execution, fake money)")
                                    .build()
                    ))
                    .build());

            blocks.add(DividerBlock.builder().build());

            // Balances
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                            .text("*Current Balances:*")
                            .build())
                    .build());

            // Free/Locked/Total from AccountSummary (with fallbacks)
            BigDecimal usdcFree = summary.usdcBalance();
            BigDecimal usdcLocked = summary.usdcLocked() != null ? summary.usdcLocked() : BigDecimal.ZERO;
            BigDecimal usdcTotal = summary.usdcTotal() != null ? summary.usdcTotal() : usdcFree.add(usdcLocked);
            BigDecimal ethFree = summary.ethBalance();
            BigDecimal ethLocked = summary.ethLocked() != null ? summary.ethLocked() : BigDecimal.ZERO;
            BigDecimal ethTotal = summary.ethTotal() != null ? summary.ethTotal() : ethFree.add(ethLocked);
            BigDecimal totalValueFree = summary.totalValueFree() != null ? summary.totalValueFree() : summary.totalValue();
            BigDecimal totalValueTotal = summary.totalValueTotal() != null ? summary.totalValueTotal() : totalValueFree;

            blocks.add(SectionBlock.builder()
                    .fields(Arrays.asList(
                            MarkdownTextObject.builder()
                                    .text(String.format("*💰 USDC:*\nFree: $%.2f | Locked: $%.2f | Total: $%.2f",
                                            usdcFree, usdcLocked, usdcTotal))
                                    .build(),
                            MarkdownTextObject.builder()
                                    .text(String.format("*📊 ETH:*\nFree: %.6f ETH | Locked: %.6f ETH | Total: %.6f ETH",
                                            ethFree, ethLocked, ethTotal))
                                    .build(),
                            MarkdownTextObject.builder()
                                    .text(String.format("*📈 Total Value:*\nFree: $%.2f | Total: $%.2f",
                                            totalValueFree, totalValueTotal))
                                    .build()
                    ))
                    .build());

            blocks.add(DividerBlock.builder().build());

            // Trade Statistics
            int totalTrades = summary.totalTrades();
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                            .text(String.format("*📊 Trading Statistics:*\n%d total trades executed", totalTrades))
                            .build())
                    .build());

            // Recent trades (last 3)
            if (!trades.isEmpty()) {
                blocks.add(DividerBlock.builder().build());
                blocks.add(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text("*Recent Trades:*")
                                .build())
                        .build());

                int count = 0;
                for (BinanceTrade trade : trades) {
                    if (count++ >= 3) break;
                    
                    String side = trade.side();
                    String tradeEmoji = side.equals("BUY") ? "🟢" : "🔴";
                    BigDecimal qty = trade.qty();
                    BigDecimal price = trade.price();

                    blocks.add(ContextBlock.builder()
                            .elements(Arrays.asList(
                                    MarkdownTextObject.builder()
                                            .text(String.format("%s *%s* %.4f ETH @ $%.2f",
                                                    tradeEmoji,
                                                    side,
                                                    qty,
                                                    price))
                                            .build()
                            ))
                            .build());
                }
            }

            // Action buttons
            blocks.add(DividerBlock.builder().build());
            blocks.add(ActionsBlock.builder()
                    .elements(Arrays.asList(
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("View All Trades").build())
                                    .actionId("view_trades")
                                    .value(userId)
                                    .build(),
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("Analyze Market").build())
                                    .actionId("analyze_market")
                                    .value(userId)
                                    .style("primary")
                                    .build()
                    ))
                    .build());

            sendBlockMessage(channelId, blocks);

        } catch (Exception e) {
            log.error("Error handling portfolio command", e);
            sendMessage(channelId, "❌ Error fetching portfolio: " + e.getMessage());
        }
    }

    /**
     * Handle /eth trades command - Complete trade history with details
     * Shows commission, timestamps, order IDs, and all trade details from Binance Testnet
     */
    public void handleTradesCommand(String userId, String channelId) {
        try {
            log.info("Handling /eth trades for user: {}", userId);

            List<BinanceTrade> trades = tradingService.getRecentTrades(50);

            List<LayoutBlock> blocks = new ArrayList<>();

            // Header
            blocks.add(HeaderBlock.builder()
                    .text(PlainTextObject.builder().text("📜 Trade History").build())
                    .build());

            // Mode indicator
            blocks.add(ContextBlock.builder()
                    .elements(List.of(
                            MarkdownTextObject.builder()
                                    .text(String.format("🧪 | TESTNET - Binance Testnet | %d trades",
                                            trades.size()))
                                    .build()
                    ))
                    .build());

            blocks.add(DividerBlock.builder().build());

            if (trades.isEmpty()) {
                blocks.add(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text("*No trades yet*\n\nStart trading with `/eth buy $100`")
                                .build())
                        .build());
            } else {
                // Show trades (most recent first)
                blocks.add(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text("*Recent Trades* (most recent first)")
                                .build())
                        .build());

                int count = 0;
                for (BinanceTrade trade : trades) {
                    if (count++ >= 20) { // Show max 20 trades
                        blocks.add(ContextBlock.builder()
                                .elements(List.of(
                                        MarkdownTextObject.builder()
                                                .text(String.format("_... and %d more trades. Use `/eth portfolio` for summary._", 
                                                        trades.size() - 20))
                                                .build()
                                ))
                                .build());
                        break;
                    }

                    String side = trade.side();
                    BigDecimal qty = trade.qty();
                    BigDecimal price = trade.price();
                    BigDecimal quoteQty = trade.quoteQty();
                    Instant timeInstant = trade.timeInstant();
                    
                    String timeStr = java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss")
                            .withZone(java.time.ZoneId.systemDefault())
                            .format(timeInstant);

                    String tradeEmoji = side.equals("BUY") ? "🟢" : "🔴";

                    // Build trade details
                    StringBuilder tradeDetails = new StringBuilder();
                    tradeDetails.append(String.format("%s *%s* %.6f ETH @ $%.2f = $%.2f",
                            tradeEmoji, side, qty, price, quoteQty));

                    // Add commission if available
                    BigDecimal commission = trade.commission();
                    if (commission != null && commission.compareTo(BigDecimal.ZERO) > 0) {
                        tradeDetails.append(String.format("\n   Fee: %.6f %s", commission, trade.commissionAsset()));
                    }

                    tradeDetails.append(String.format("\n   🕐 %s | Order #%s", timeStr, trade.orderId()));

                    blocks.add(SectionBlock.builder()
                            .text(MarkdownTextObject.builder()
                                    .text(tradeDetails.toString())
                                    .build())
                            .build());

                    // Add divider between trades for readability
                    if (count < min(20, trades.size())) {
                        blocks.add(DividerBlock.builder().build());
                    }
                }
            }

            // Action buttons
            blocks.add(DividerBlock.builder().build());
            blocks.add(ActionsBlock.builder()
                    .elements(Arrays.asList(
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("💼 Portfolio").build())
                                    .value("portfolio")
                                    .actionId("portfolio_button")
                                    .build(),
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("⚡ Quick Recommend").build())
                                    .value("recommend")
                                    .actionId("recommend_button")
                                    .build()
                    ))
                    .build());

            sendBlockMessage(channelId, blocks);

        } catch (Exception e) {
            log.error("Error handling trades command", e);
            sendMessage(channelId, "❌ Error fetching trade history: " + e.getMessage());
        }
    }

    /**
     * Send analysis message with interactive buttons
     */
    private void sendAnalysisMessage(String channelId, String analysis, String userId) {
        try {
            List<LayoutBlock> blocks = new ArrayList<>();

            // Header
            blocks.add(HeaderBlock.builder()
                    .text(PlainTextObject.builder().text("📊 ETH Market Analysis").build())
                    .build());

            // Split analysis into chunks if too long (Slack limit: 3000 chars per block)
            String cleanAnalysis = analysis.replace("```", ""); // Remove code blocks
            int maxLength = 2900; // Leave some buffer
            
            if (cleanAnalysis.length() > maxLength) {
                // Split into multiple section blocks
                int start = 0;
                while (start < cleanAnalysis.length()) {
                    int end = min(start + maxLength, cleanAnalysis.length());
                    
                    // Try to break at newline
                    if (end < cleanAnalysis.length()) {
                        int lastNewline = cleanAnalysis.lastIndexOf('\n', end);
                        if (lastNewline > start) {
                            end = lastNewline;
                        }
                    }
                    
                    String chunk = cleanAnalysis.substring(start, end);
                    blocks.add(SectionBlock.builder()
                            .text(MarkdownTextObject.builder().text(chunk).build())
                            .build());
                    
                    start = end;
                }
            } else {
                blocks.add(SectionBlock.builder()
                        .text(MarkdownTextObject.builder().text(cleanAnalysis).build())
                        .build());
            }

            // Action buttons
            blocks.add(DividerBlock.builder().build());
            blocks.add(ActionsBlock.builder()
                    .elements(Arrays.asList(
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("Execute Trade").build())
                                    .actionId("execute_trade")
                                    .value(userId)
                                    .style("primary")
                                    .build(),
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("Get Details").build())
                                    .actionId("get_details")
                                    .value(userId)
                                    .build(),
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("Refresh").build())
                                    .actionId("refresh_analysis")
                                    .value(userId)
                                    .build()
                    ))
                    .build());

            sendBlockMessage(channelId, blocks);

        } catch (Exception e) {
            log.error("Error sending analysis message", e);
            // Fallback to simple text message
            sendMessage(channelId, "📊 *ETH Market Analysis*\n\n" + analysis);
        }
    }

    /**
     * Send recommendation message with formatted output
     */
    private void sendRecommendationMessage(String channelId, net.pautet.softs.demospring.dto.TradeRecommendation recommendation, String userId) {
        try {
            List<LayoutBlock> blocks = new ArrayList<>();

            // Header
            blocks.add(HeaderBlock.builder()
                    .text(PlainTextObject.builder().text("⚡ Quick Trading Recommendation").build())
                    .build());

            // Main recommendation content using Slack formatting
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder().text(recommendation.toSlackFormat()).build())
                    .build());

            // Action buttons
            blocks.add(DividerBlock.builder().build());
            blocks.add(ActionsBlock.builder()
                    .elements(Arrays.asList(
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("Full Analysis").build())
                                    .actionId("analyze_market")
                                    .value(userId)
                                    .style("primary")
                                    .build(),
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("Portfolio").build())
                                    .actionId("view_portfolio")
                                    .value(userId)
                                    .build(),
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("Refresh").build())
                                    .actionId("refresh_recommendation")
                                    .value(userId)
                                    .build()
                    ))
                    .build());

            sendBlockMessage(channelId, blocks);

        } catch (Exception e) {
            log.error("Error sending recommendation message", e);
            // Fallback to simple text
            sendMessage(channelId, "⚡ *Quick Recommendation*\n\n" + recommendation.toDisplayString());
        }
    }

    /**
     * Send simple text message
     */
    public void sendMessage(String channelId, String text) {
        try {
            var request = ChatPostMessageRequest.builder()
                    .token(botToken)
                    .channel(channelId)
                    .text(text)
                    .build();

            var response = methods.chatPostMessage(request);
            
            if (!response.isOk()) {
                log.error("Failed to send message: {}", response.getError());
            }
        } catch (IOException | SlackApiException e) {
            log.error("Error sending message to Slack", e);
        }
    }

    /**
     * Send message with blocks (rich formatting)
     */
    public void sendBlockMessage(String channelId, List<LayoutBlock> blocks) {
        try {
            var request = ChatPostMessageRequest.builder()
                    .token(botToken)
                    .channel(channelId)
                    .blocks(blocks)
                    .text("ETH Trading Bot Message") // Fallback text
                    .build();

            var response = methods.chatPostMessage(request);
            
            if (!response.isOk()) {
                log.error("Failed to send block message: {}", response.getError());
            }
        } catch (IOException | SlackApiException e) {
            log.error("Error sending block message to Slack", e);
        }
    }

    /**
     * Handle /eth context command - Show AI context data for debugging
     * Displays all market data, indicators, and portfolio info being passed to AI
     */
    public void handleContextCommand(String userId, String channelId) {
        try {
            log.info("Handling /eth context for user: {}", userId);

            // Send initial message
            sendMessage(channelId, "🔍 Gathering AI context data...");

            // Get all context using centralized service
            TradingContextService.TradingContext context = tradingContextService.gatherCompleteContext();
            
            List<LayoutBlock> blocks = new ArrayList<>();

            // Header
            blocks.add(HeaderBlock.builder()
                    .text(PlainTextObject.builder().text("🔍 AI Context Data").build())
                    .build());
            
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                            .text("_This is the data being passed to the AI model_")
                            .build())
                    .build());
            
            blocks.add(DividerBlock.builder().build());

            // Market Data
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                            .text(String.format("""
                                    *📊 Market Data (24h)*
                                    Price: `$%.2f`
                                    Change: `%+.2f%% ($%+.2f)`
                                    High: `$%.2f`
                                    Low: `$%.2f`
                                    Volume: `%.0f ETH`
                                    """,
                                    context.ticker.lastPriceAsDouble(),
                                    context.ticker.priceChangePercentAsDouble(),
                                    context.ticker.priceChange().doubleValue(),
                                    context.ticker.highPrice().doubleValue(),
                                    context.ticker.lowPrice().doubleValue(),
                                    context.ticker.volumeAsDouble()))
                            .build())
                    .build());
            
            blocks.add(DividerBlock.builder().build());

            // Technical Indicators - 5m
            if (hasError(context.tech5m)) {
                blocks.add(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text("*📈 Technical Indicators (5m)*\n" + formatDataError(context.tech5m))
                                .build())
                        .build());
            } else {
                blocks.add(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text(String.format("""
                                        *📈 Technical Indicators (5m)*
                                        RSI: `%.2f` - %s
                                        MACD: `%.2f` (Signal: `%.2f`, Hist: `%.2f`)
                                        BB Upper: `%.2f` | Middle: `%.2f` | Lower: `%.2f`
                                        EMA20: `%.2f` | EMA50: `%.2f`
                                        Data Points: %d | Quality: %s
                                        """,
                                        getDoubleValue(context.tech5m, "rsi"),
                                        getStringValue(context.tech5m, "rsiSignal"),
                                        getDoubleValue(context.tech5m, "macd"),
                                        getDoubleValue(context.tech5m, "macdSignal"),
                                        getDoubleValue(context.tech5m, "macdHistogram"),
                                        getDoubleValue(context.tech5m, "bbUpper"),
                                        getDoubleValue(context.tech5m, "bbMiddle"),
                                        getDoubleValue(context.tech5m, "bbLower"),
                                        getDoubleValue(context.tech5m, "ema20"),
                                        getDoubleValue(context.tech5m, "ema50"),
                                        ((Number) context.tech5m.getOrDefault("dataPoints", 0)).intValue(),
                                        getStringValue(context.tech5m, "dataQuality")))
                                .build())
                        .build());
            }

            // Technical Indicators - 15m
            if (hasError(context.tech15m)) {
                blocks.add(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text("*📈 Technical Indicators (15m)*\n" + formatDataError(context.tech15m))
                                .build())
                        .build());
            } else {
                blocks.add(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text(String.format("""
                                        *📈 Technical Indicators (15m)*
                                        RSI: `%.2f` - %s
                                        MACD Hist: `%.2f`
                                        Data Points: %d | Quality: %s
                                        """,
                                        getDoubleValue(context.tech15m, "rsi"),
                                        getStringValue(context.tech15m, "rsiSignal"),
                                        getDoubleValue(context.tech15m, "macdHistogram"),
                                        ((Number) context.tech15m.getOrDefault("dataPoints", 0)).intValue(),
                                        getStringValue(context.tech15m, "dataQuality")))
                                .build())
                        .build());
            }

            // Technical Indicators - 1h
            if (hasError(context.tech1h)) {
                blocks.add(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text("*📈 Technical Indicators (1h)*\n" + formatDataError(context.tech1h))
                                .build())
                        .build());
            } else {
                blocks.add(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text(String.format("""
                                        *📈 Technical Indicators (1h)*
                                        RSI: `%.2f` - %s
                                        MACD Hist: `%.2f`
                                        Trend: %s
                                        Data Points: %d | Quality: %s
                                        """,
                                        getDoubleValue(context.tech1h, "rsi"),
                                        getStringValue(context.tech1h, "rsiSignal"),
                                        getDoubleValue(context.tech1h, "macdHistogram"),
                                        getStringValue(context.tech1h, "trend"),
                                        ((Number) context.tech1h.getOrDefault("dataPoints", 0)).intValue(),
                                        getStringValue(context.tech1h, "dataQuality")))
                                .build())
                        .build());
            }
            
            blocks.add(DividerBlock.builder().build());

            // Sentiment
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                            .text(String.format("""
                                    *🎭 Sentiment Analysis*
                                    Score: `%.2f`
                                    Classification: `%s`
                                    Fear & Greed: `%d` (%s)
                                    """,
                                    getDoubleValue(context.sentiment, "overallScore"),
                                    getStringValue(context.sentiment, "classification"),
                                    (int) getDoubleValue(context.sentiment, "fearGreedIndex"),
                                    getStringValue(context.sentiment, "fearGreedLabel")))
                            .build())
                    .build());
            
            blocks.add(DividerBlock.builder().build());

            // Portfolio
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                            .text(String.format("""
                                    *💼 Portfolio*
                                    USDC: `$%.2f`
                                    ETH: `%.6f` ($%.2f)
                                    Total: `$%.2f`
                                    Total Trades: %d
                                    Mode: %s
                                    """,
                                    context.portfolio.usdBalance().doubleValue(),
                                    context.portfolio.ethBalance().doubleValue(),
                                    context.portfolio.ethValue().doubleValue(),
                                    context.portfolio.totalValue().doubleValue(),
                                    context.portfolio.totalTrades(),
                                    "TESTNET"))
                            .build())
                    .build());
            
            blocks.add(DividerBlock.builder().build());
            
            // Trading Memory - AI's context from previous recommendations
            try {
                String tradingMemory = context.tradingMemory;
                
                blocks.add(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text("*🧠 AI Trading Memory*\n_Previous recommendations and patterns the AI remembers_")
                                .build())
                        .build());
                
                // Split memory into chunks if too long (Slack has message limits)
                if (tradingMemory.length() > 2000) {
                    // Show first 2000 chars and indicate truncation
                    String truncated = tradingMemory.substring(0, 2000);
                    blocks.add(SectionBlock.builder()
                            .text(MarkdownTextObject.builder()
                                    .text("```" + truncated + "...\n[truncated]```")
                                    .build())
                            .build());
                } else {
                    blocks.add(SectionBlock.builder()
                            .text(MarkdownTextObject.builder()
                                    .text("```" + tradingMemory + "```")
                                    .build())
                            .build());
                }
            } catch (Exception e) {
                blocks.add(SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text("*🧠 AI Trading Memory*\n```Error loading memory: " + e.getMessage() + "```")
                                .build())
                        .build());
            }

            blocks.add(ContextBlock.builder()
                    .elements(List.of(
                            MarkdownTextObject.builder()
                                    .text("💡 This is the EXACT context passed to the AI for `/eth recommend`")
                                    .build()
                    ))
                    .build());

            sendBlockMessage(channelId, blocks);
            
        } catch (Exception e) {
            log.error("Error handling context command", e);
            sendMessage(channelId, "❌ Error gathering context: " + e.getMessage());
        }
    }
    
    // Helper methods for safe value extraction
    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0.0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return 0.0;
    }
    
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "N/A";
    }
    
    private boolean hasError(Map<String, Object> map) {
        return map.containsKey("error");
    }
    
    private String formatDataError(Map<String, Object> errorMap) {
        int received = ((Number) errorMap.getOrDefault("candlesReceived", 0)).intValue();
        int required = ((Number) errorMap.getOrDefault("candlesRequired", 50)).intValue();
        String environment = (String) errorMap.getOrDefault("environment", "unknown");
        String explanation = (String) errorMap.getOrDefault("explanation", "Insufficient historical data");
        String recommendation = (String) errorMap.getOrDefault("recommendation", "Wait for more data");
        
        return String.format("""
                ⚠️  *Data Quality Issue*
                Got: %d candles
                Need: %d+ candles
                Environment: %s
                
                *Explanation:*
                %s
                
                *Recommendation:*
                %s
                """, received, required, environment, explanation, recommendation);
    }

    /**
     * Handle /eth reset command - Reset testnet account to 0 ETH
     * TESTNET ONLY - Sells all ETH to USDC
     */
    public void handleResetCommand(String userId, String channelId) {
        try {
            log.warn("⚠️  Handling /eth reset for user: {} - TESTNET RESET", userId);

            // Send warning message
            sendMessage(channelId, "⚠️  Resetting testnet account... This will sell all ETH!");

            // Execute reset
            Map<String, Object> result = tradingService.resetAccount();
            
            @SuppressWarnings("unchecked")
            List<String> actions = (List<String>) result.get("actions");
            BigDecimal finalEth = (BigDecimal) result.get("finalEthBalance");
            BigDecimal finalUsdc = (BigDecimal) result.get("finalUsdcBalance");
            BigDecimal finalBtc = (BigDecimal) result.get("finalBtcBalance");
            
            List<LayoutBlock> blocks = new ArrayList<>();

            // Header
            blocks.add(HeaderBlock.builder()
                    .text(PlainTextObject.builder().text("🔄 Testnet Reset Complete").build())
                    .build());

            // Actions taken
            StringBuilder actionsText = new StringBuilder("*Actions:*\n");
            for (String action : actions) {
                actionsText.append("• ").append(action).append("\n");
            }
            
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder().text(actionsText.toString()).build())
                    .build());
            
            blocks.add(DividerBlock.builder().build());
            
            // Final balances
            String balanceText = String.format("*Final Balances:*\n💵 USDC: `$%.2f`\n⚡ ETH: `%.6f`\n₿ BTC: `%.8f`\n\n_Ready for fresh trading!_", 
                finalUsdc.doubleValue(), 
                finalEth.doubleValue(),
                finalBtc.doubleValue());
            
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder().text(balanceText).build())
                    .build());
            
            blocks.add(ContextBlock.builder()
                    .elements(List.of(
                            MarkdownTextObject.builder()
                                    .text("⚠️  *TESTNET ONLY* - This command only works on Binance Testnet")
                                    .build()
                    ))
                    .build());

            sendBlockMessage(channelId, blocks);
            
        } catch (Exception e) {
            log.error("Error handling reset command", e);
            sendMessage(channelId, "❌ Failed to reset account: " + e.getMessage());
        }
    }

    /**
     * Handle /eth recommendations command - Show AI recommendation history
     * Displays past recommendations with Block Kit table format
     */
    public void handleRecommendationsHistoryCommand(String userId, String channelId, String text) {
        try {
            log.info("Handling /eth recommendations for user: {}", userId);
            
            // Parse optional limit from command
            int limit = 10; // default
            if (text != null && text.contains(" ")) {
                String[] parts = text.split(" ");
                if (parts.length > 1) {
                    try {
                        limit = Integer.parseInt(parts[1]);
                        limit = min(max(limit, 1), 20); // Clamp between 1-20
                    } catch (NumberFormatException e) {
                        // Use default
                    }
                }
            }
            
            // Fetch recent recommendations
            var recommendations = recommendationRepository.findRecentSince(
                java.time.LocalDateTime.now().minusDays(7)
            ).stream()
            .limit(limit)
            .toList();
            
            if (recommendations.isEmpty()) {
                sendMessage(channelId, "📊 No recommendations found in the last 7 days.\n\nGenerate one with `/eth recommend`");
                return;
            }
            
            // Build Block Kit message with table-like format
            List<LayoutBlock> blocks = new ArrayList<>();
            
            // Header
            blocks.add(HeaderBlock.builder()
                    .text(PlainTextObject.builder()
                            .text(String.format("🧠 AI Recommendation History (Last %d)", recommendations.size()))
                            .build())
                    .build());
            
            // Stats summary
            long executed = recommendations.stream().filter(RecommendationHistory::getExecuted).count();
            long buyCount = recommendations.stream().filter(r -> r.getSignal() == TradeRecommendation.Signal.BUY).count();
            long sellCount = recommendations.stream().filter(r -> r.getSignal() == TradeRecommendation.Signal.SELL).count();
            long holdCount = recommendations.stream().filter(r -> r.getSignal() == TradeRecommendation.Signal.HOLD).count();
            
            String statsText = String.format("""
                    *Summary:* %d total | %d executed (%.0f%%)
                    📈 BUY: %d | 📉 SELL: %d | ⏸️ HOLD: %d
                    """, 
                    recommendations.size(),
                    executed,
                    recommendations.size() > 0 ? (executed * 100.0 / recommendations.size()) : 0,
                    buyCount, sellCount, holdCount);
            
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder().text(statsText).build())
                    .build());
            
            blocks.add(DividerBlock.builder().build());
            
            // Table header
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                            .text("*Time | Signal | Confidence | Amount | Status*")
                            .build())
                    .build());
            
            // Add each recommendation as a row
            for (RecommendationHistory rec : recommendations) {
                String signalEmoji = switch (rec.getSignal()) {
                    case BUY -> "📈";
                    case SELL -> "📉";
                    case HOLD -> "⏸️";
                };
                
                String confidenceEmoji = switch (rec.getConfidence()) {
                    case HIGH -> "🔥";
                    case MEDIUM -> "✅";
                    case LOW -> "⚠️";
                };
                
                String amountStr = rec.getAmount() != null
                        ? (rec.getAmountType() == TradeRecommendation.AmountType.USD
                            ? String.format("$%.0f", rec.getAmount())
                            : String.format("%.4f ETH", rec.getAmount()))
                        : "—";
                
                String statusEmoji = rec.getExecuted() ? "✅" : "—";
                
                String timeStr = formatTimestamp(rec.getTimestamp());
                
                // Format as table row
                String rowText = String.format("`%s` | %s `%s` | %s `%s` | `%s` | %s",
                        timeStr,
                        signalEmoji,
                        rec.getSignal(),
                        confidenceEmoji,
                        rec.getConfidence(),
                        amountStr,
                        statusEmoji);
                
                blocks.add(SectionBlock.builder()
                        .text(MarkdownTextObject.builder().text(rowText).build())
                        .build());
                
                // Add memory if present
                if (rec.getAiMemory() != null && !rec.getAiMemory().isEmpty()) {
                    String memoryText = "_Memory:_ " + String.join(" • ", rec.getAiMemory());
                    blocks.add(ContextBlock.builder()
                            .elements(List.of(
                                    MarkdownTextObject.builder().text(memoryText).build()
                            ))
                            .build());
                }
            }
            
            blocks.add(DividerBlock.builder().build());
            
            // Footer with tips
            blocks.add(ContextBlock.builder()
                    .elements(List.of(
                            MarkdownTextObject.builder()
                                    .text("💡 _Use `/eth recommendations 20` to see more_")
                                    .build()
                    ))
                    .build());
            
            // Action buttons
            blocks.add(ActionsBlock.builder()
                    .elements(List.of(
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("🔄 New Recommendation").build())
                                    .value("refresh")
                                    .actionId("refresh_recommendation")
                                    .style("primary")
                                    .build(),
                            ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("📊 View Portfolio").build())
                                    .value("portfolio")
                                    .actionId("view_portfolio")
                                    .build()
                    ))
                    .build());
            
            sendBlockMessage(channelId, blocks);
            
        } catch (Exception e) {
            log.error("Error handling recommendations history command", e);
            sendMessage(channelId, "❌ Error fetching recommendation history: " + e.getMessage());
        }
    }
    
    /**
     * Format timestamp to relative time string
     */
    private String formatTimestamp(java.time.LocalDateTime timestamp) {
        var now = java.time.LocalDateTime.now();
        var duration = java.time.Duration.between(timestamp, now);
        
        long hours = duration.toHours();
        long minutes = duration.toMinutes();
        
        if (hours > 24) {
            return String.format("%dd ago", duration.toDays());
        } else if (hours > 0) {
            return String.format("%dh ago", hours);
        } else {
            return String.format("%dm ago", minutes);
        }
    }

    /**
     * Send help message
     */
    public void handleHelpCommand(String channelId) {
        List<LayoutBlock> blocks = new ArrayList<>();

        blocks.add(HeaderBlock.builder()
                .text(PlainTextObject.builder().text("🤖 ETH Trading Bot Commands").build())
                .build());

        blocks.add(SectionBlock.builder()
                .text(MarkdownTextObject.builder()
                        .text("""
                                *📊 ANALYSIS*
                                `/eth recommend` ⚡ - Quick trading signal (fast, cost-optimized)
                                `/eth recommendations` 🧠 - View AI recommendation history
                                `/eth analyze` - Full market analysis with AI
                                `/eth price` - Quick price check
                                
                                *💼 PORTFOLIO*
                                `/eth portfolio` - View portfolio & stats
                                `/eth trades` - View trade history
                                
                                *💰 TRADING*
                                `/eth buy <amount>` - Buy ETH (e.g., /eth buy $500)
                                `/eth sell <amount>` - Sell ETH (e.g., /eth sell 0.5)
                                
                                *🔧 TESTNET*
                                `/eth reset` - Reset account to 0 ETH (sells all)
                                
                                *🔍 DEBUG*
                                `/eth context` - Show AI context data (troubleshooting)
                                
                                *💡 TIP:* Use `/eth recommend` for frequent checks (every 15-60min)
                                Use `/eth recommendations` to see AI's memory evolution
                                Use `/eth analyze` for detailed investigation
                                
                                *ℹ️ Need help?* Just type `/eth help`
                                """)
                        .build())
                .build());

        sendBlockMessage(channelId, blocks);
    }
}
