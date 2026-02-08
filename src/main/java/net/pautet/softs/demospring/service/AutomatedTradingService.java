package net.pautet.softs.demospring.service;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.conversations.ConversationsCreateRequest;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.response.conversations.ConversationsCreateResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.model.Conversation;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.config.SlackConfig;
import net.pautet.softs.demospring.dto.AccountSummary;
import net.pautet.softs.demospring.dto.BinanceOrderResponse;
import net.pautet.softs.demospring.dto.TradeRecommendation;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Automated Trading Service
 * Executes automated trading based on AI recommendations:
 * - Runs every 15 minutes
 * - Gets quick recommendation from AI
 * - Automatically executes trades on Binance Testnet
 * - Posts all activity to #ethbot Slack channel
 * SAFETY: Only executes on testnet with fake money
 */
@Service
@Slf4j
public class AutomatedTradingService {

    private final QuickRecommendationServiceGemini quickRecommendationService;
    private final BinanceTradingService tradingService;
    private final RecommendationPersistenceService persistenceService;
    private final BinanceApiService binanceApiService;
    private final SlackConfig slackConfig;
    private final Slack slack;
    
    private static final String BOT_CHANNEL = "ethbot";
    private static final String DEFAULT_USERNAME = "automated-trader";

    public AutomatedTradingService(QuickRecommendationServiceGemini quickRecommendationService,
                                   BinanceTradingService tradingService,
                                   RecommendationPersistenceService persistenceService,
                                   SlackConfig slackConfig,
                                   BinanceApiService binanceApiService) {
        this.quickRecommendationService = quickRecommendationService;
        this.tradingService = tradingService;
        this.persistenceService = persistenceService;
        this.slackConfig = slackConfig;
        this.slack = Slack.getInstance();
        this.binanceApiService = binanceApiService;
    }

    /**
     * Initialize #ethbot channel on application startup
     * Ensures channel is ready before first scheduled cycle
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeOnStartup() {
        log.debug("Initializing automated trading system...");
        
        try {
            String channelId = ensureEthBotChannelExists();
            if (channelId != null) {
                log.debug("#{} channel ready: {}", BOT_CHANNEL, channelId);
            } else {
                log.warn("Failed to initialize #{} channel", BOT_CHANNEL);
            }
        } catch (Exception e) {
            log.error("Error during startup initialization", e);
        }
    }

    /**
     * Resolve ETH quantity to sell given amount and type. If amount is USD, divide by price and
     * validate using exchange precision/filters.
     */
    private BigDecimal resolveSellEthQuantity(BigDecimal amount, TradeRecommendation.AmountType type, BigDecimal price) {
        if (amount == null) return BigDecimal.ZERO;
        if (type == TradeRecommendation.AmountType.USD) {
            BigDecimal rawQty = amount.divide(price, 8, java.math.RoundingMode.DOWN);
            BigDecimal adjusted = tradingService.validateAndAdjustQuantity(rawQty);
            if (adjusted == null) {
                throw new IllegalArgumentException("Computed ETH quantity below exchange minimum");
            }
            return adjusted;
        }
        return amount; // Already ETH
    }

    /**
     * Automated trading execution - runs every 15 minutes
     * Schedule: 0 0/15 * * * * = Every 15 minutes
     */
    @Scheduled(cron = "0 0/15 * * * *")
    public void executeAutomatedTrading() {
        log.info("Starting automated trading cycle");
        
        try {
            // 1. Ensure #ethbot channel exists
            String channelId = ensureEthBotChannelExists();
            if (channelId == null) {
                log.error("Failed to create/find #ethbot channel, skipping automated trading");
                return;
            }
            
            // 2. Post start message
            if (log.isDebugEnabled()) {
                postToEthBot(channelId, "Automated Trading Cycle Started\n\nAnalyzing market conditions and portfolio...");
            }
            
            // 3. Get AI recommendation (structured output)
            TradeRecommendation recommendation = quickRecommendationService.getQuickRecommendation(DEFAULT_USERNAME);
            log.info("AI Recommendation received:\n{}", recommendation.toDisplayString());
            
            // 4. Post recommendation to channel
            postRecommendation(channelId, recommendation);
            
            // 5. Execute trade if applicable
            if (shouldExecuteTrade(recommendation)) {
                executeAndReportTrade(channelId, recommendation);
            } else {
                postToEthBot(channelId, String.format(
                        "Trade Not Executed\n\nSignal: %s\nConfidence: %s\nReason: %s",
                        recommendation.signal(),
                        recommendation.confidence(),
                        getSkipReason(recommendation)
                ));
            }

            // 6. Post completion
            if (log.isDebugEnabled()) {
                postToEthBot(channelId, "Automated Trading Cycle Complete\n\nNext cycle in 15 minutes.");
            }

        } catch (Exception e) {
            log.error("Error in automated trading cycle", e);
            try {
                String channelId = findEthBotChannel();
                if (channelId != null) {
                    postToEthBot(channelId, String.format(
                            "Error in Automated Trading\n\nError: %s\n\nWill retry next cycle.", e.getMessage()));
                }
            } catch (Exception ex) {
                log.error("Failed to post error message", ex);
            }
        }
        log.info("Ended automated trading cycle");
    }

    private record FeeCheck(BigDecimal pctToTp, BigDecimal roundTrip) {}

    /**
     * Compute fee comparison data for BUY with TP1. Returns null if not applicable
     * or if required data (fees/price) cannot be determined.
     */
    private FeeCheck computeFeeCheck(TradeRecommendation rec) {
        try {
            if (rec.signal() != TradeRecommendation.Signal.BUY || rec.tp1() == null) {
                return null;
            }
            var fees = binanceApiService.getTradeFees(BinanceTradingService.SYMBOL_ETHUSDC);
            
            // Determine entry fee based on order type (Maker for LIMIT, Taker for MARKET)
            BigDecimal entryFee = (rec.entryType() == TradeRecommendation.EntryType.LIMIT)
                    ? fees.makerCommission()
                    : fees.takerCommission();
            
            // Exit fee (TP/SL) usually hits as Taker (conservative assumption)
            BigDecimal exitFee = fees.takerCommission();
            
            BigDecimal roundTrip = entryFee.add(exitFee);

            BigDecimal entryPrice = rec.entryType() == TradeRecommendation.EntryType.LIMIT && rec.entryPrice() != null
                    ? rec.entryPrice()
                    : tradingService.getCurrentPrice();
            if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }

            BigDecimal tpDelta = rec.tp1().subtract(entryPrice);
            BigDecimal pctToTp = tpDelta.divide(entryPrice, 8, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
            return new FeeCheck(pctToTp, roundTrip);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Determine if we should execute the trade
     */
    private boolean shouldExecuteTrade(TradeRecommendation rec) {
        // Use the record's built-in validation
        boolean baseOk = rec.isActionable() &&
                (rec.confidence() == TradeRecommendation.Confidence.HIGH ||
                 rec.confidence() == TradeRecommendation.Confidence.MEDIUM);
        if (!baseOk) return false;

        // Fees-aware gating: for BUY with TP1 defined, ensure TP1 clears round-trip fees
        FeeCheck fees = computeFeeCheck(rec);
        if (fees != null) {
            return fees.pctToTp().compareTo(fees.roundTrip()) > 0;
        }

        return true;
    }

    /**
     * Get reason for skipping trade
     */
    private String getSkipReason(TradeRecommendation rec) {
        if (rec.signal() == TradeRecommendation.Signal.HOLD) {
            return "Signal is HOLD - no action needed";
        }
        if (!rec.isActionable()) {
            return "No amount specified or invalid amount";
        }
        if (rec.confidence() == TradeRecommendation.Confidence.LOW) {
            return "Confidence too low (LOW) - minimum MEDIUM required";
        }
        // Fees-based reason. computeFeeCheck() is exception-safe and returns null on failure.
        FeeCheck fees = computeFeeCheck(rec);
        if (fees != null && fees.pctToTp().compareTo(fees.roundTrip()) <= 0) {
            return String.format("TP1 (%.2f%%) does not clear round-trip fees (%.2f%%)",
                    fees.pctToTp().multiply(BigDecimal.valueOf(100)).doubleValue(),
                    fees.roundTrip().multiply(BigDecimal.valueOf(100)).doubleValue());
        }
        return "Unknown reason";
    }

    /**
     * Execute trade and report results
     */
    private void executeAndReportTrade(String channelId, TradeRecommendation rec) {
        try {
            postToEthBot(channelId, String.format(
                    "Executing Trade\n\nSignal: %s\nConfidence: %s\nAmount: %s %s\n\nExecuting on Binance Testnet...",
                    rec.signal(),
                    rec.confidence(),
                    rec.amount(),
                    rec.amountType()
            ));
            
            // Execute the trade
            BigDecimal amount = rec.amount();
            BinanceOrderResponse order;

            if (rec.signal() == TradeRecommendation.Signal.BUY) {
                // BUY: prefer LIMIT if requested, else MARKET by USDC amount
                if (rec.entryType() == TradeRecommendation.EntryType.LIMIT && rec.entryPrice() != null) {
                    order = tradingService.buyETHLimitUSD(amount, rec.entryPrice());
                } else {
                    order = tradingService.buyETH(amount);
                }
            } else { // SELL
                // SELL: if LIMIT requested and entryPrice present, sell at limit
                if (rec.entryType() == TradeRecommendation.EntryType.LIMIT && rec.entryPrice() != null) {
                    BigDecimal ethQty = resolveSellEthQuantity(amount, rec.amountType(), rec.entryPrice());
                    order = tradingService.sellETHLimit(ethQty, rec.entryPrice());
                } else {
                    // MARKET sell expects ETH quantity; convert if amount is in USD
                    BigDecimal ethQty = rec.amountType() == TradeRecommendation.AmountType.USD
                            ? resolveSellEthQuantity(amount, TradeRecommendation.AmountType.USD, tradingService.getCurrentPrice())
                            : amount;
                    order = tradingService.sellETH(ethQty);
                }
            }
            
            // Get updated portfolio
            AccountSummary portfolio = tradingService.getAccountSummary();
            
            // Save execution record (mark executed only if filled)
            try {
                boolean executed = order.isFilled();
                persistenceService.saveRecommendation(rec, executed, order, null);
                log.info("Saved executed trade record: order {}", order.orderId());
            } catch (Exception e) {
                log.error("Failed to save execution record (non-critical)", e);
            }
            
            // If BUY was MARKET and filled, place OCO exit if TP/SL provided
            try {
                if (rec.signal() == TradeRecommendation.Signal.BUY
                        && rec.entryType() != TradeRecommendation.EntryType.LIMIT
                        && order.isFilled()
                        && rec.tp1() != null && rec.stopLoss() != null) {
                    var oco = tradingService.placeOcoSellExit(order.executedQty(), rec.tp1(), rec.stopLoss());
                    // Persist OCO linkage
                    try {
                        persistenceService.attachOcoDetailsByEntryOrderId(order.orderId(), oco);
                    } catch (Exception ignore) {}
                    postToEthBot(channelId, String.format("Placed OCO exit: TP $%.2f / SL $%.2f for %.6f ETH",
                            rec.tp1(), rec.stopLoss(), order.executedQty()));
                } else if (rec.signal() == TradeRecommendation.Signal.BUY
                        && rec.entryType() == TradeRecommendation.EntryType.LIMIT) {
                    postToEthBot(channelId, "Limit BUY placed. OCO exit will not be set until the entry fills.");
                }
            } catch (Exception e) {
                log.error("Failed to place OCO exit", e);
                postToEthBot(channelId, "Failed to place OCO exit orders (see logs)");
            }

            // Report success
            postToEthBot(channelId, formatTradeSuccess(rec, order, portfolio));
            
            log.debug("Automated trade executed successfully: {} {}", rec.signal(), amount);
        } catch (Exception e) {
            log.error("Failed to execute automated trade", e);
            postToEthBot(channelId, String.format(
                    "Trade Execution Failed\n\nSignal: %s\nAmount: %s %s\nError: %s\n\nThe trade was not executed.",
                    rec.signal(),
                    rec.amount(),
                    rec.amountType(),
                    e.getMessage()
            ));
        }
    }

    /**
     * Format successful trade message
     */
    private String formatTradeSuccess(TradeRecommendation rec, BinanceOrderResponse order, AccountSummary portfolio) {
        return String.format(
                "Trade Executed Successfully\n\n" +
                "Trade Details:\n" +
                "- Order ID: %s\n" +
                "- Type: %s\n" +
                "- Executed Qty: %s\n" +
                "- Avg Price: $%s\n" +
                "- Status: %s\n\n" +
                "Updated Portfolio:\n" +
                "- USD Balance: $%s\n" +
                "- ETH Balance: %s ETH\n" +
                "- Total Value: $%s\n" +
                "- Total Trades: %s\n\n" +
                "AI Reasoning:\n%s",
                order.orderId(),
                rec.signal(),
                order.executedQty(),
                order.getAveragePrice(),
                order.status(),
                formatNumber(portfolio.usdBalance()),
                formatNumber(portfolio.ethBalance()),
                formatNumber(portfolio.totalValue()),
                portfolio.totalTrades(),
                rec.reasoning()
        );
    }

    /**
     * Post recommendation to channel
     */
    private void postRecommendation(String channelId, TradeRecommendation rec) {
        String amountStr = rec.amount() != null 
            ? (rec.amountType() == TradeRecommendation.AmountType.USD 
                ? String.format("$%.2f", rec.amount()) 
                : String.format("%.5f ETH", rec.amount()))
            : "NONE";
        
        postToEthBot(channelId, String.format(
                "AI Recommendation Received\n\n" +
                "Signal: %s\n" +
                "Confidence: %s\n" +
                "Amount: %s\n\n" +
                "Reasoning:\n%s\n\n" +
                "---\n\n" +
                "Full Recommendation:\n%s",
                rec.signal(),
                rec.confidence(),
                amountStr,
                rec.reasoning(),
                rec.toDisplayString()
        ));
    }

    /**
     * Ensure #ethbot channel exists, create if needed
     */
    private String ensureEthBotChannelExists() {
        try {
            // Try to find existing channel
            String channelId = findEthBotChannel();
            if (channelId != null) {
                log.debug("Found existing #ethbot channel: {}", channelId);
                return channelId;
            }
            
            // Create new channel
            log.info("Creating new #ethbot channel");
            MethodsClient client = slack.methods(slackConfig.botToken());
            
            ConversationsCreateResponse response = client.conversationsCreate(
                ConversationsCreateRequest.builder()
                    .name(BOT_CHANNEL)
                    .isPrivate(false) // Public channel
                    .build()
            );
            
            if (response.isOk()) {
                channelId = response.getChannel().getId();
                log.info("Created #ethbot channel: {}", channelId);
                
                // Post welcome message
                String welcomeMsg = "Welcome to #ethbot!\n\n" +
                        "This channel is for automated ETH trading updates.\n\n" +
                        "- AI analyzes market every 15 minutes\n" +
                        "- Automatic trade execution on Binance Testnet\n" +
                        "- Real-time updates on all trades\n" +
                        "- Portfolio tracking\n\n" +
                        "Mode: Binance Testnet (fake money, real execution)\n" +
                        "Schedule: Every 15 minutes at x:00, x:15, x:30, x:45\n\n" +
                        "First trading cycle will begin at the next 15-minute mark.";
                
                postToEthBot(channelId, welcomeMsg);
                
                return channelId;
            } else {
                log.error("Failed to create channel: {}", response.getError());
                return null;
            }
            
        } catch (Exception e) {
            log.error("Error ensuring #ethbot channel exists", e);
            return null;
        }
    }

    /**
     * Find #ethbot channel
     */
    private String findEthBotChannel() {
        try {
            MethodsClient client = slack.methods(slackConfig.botToken());
            
            ConversationsListResponse response = client.conversationsList(
                ConversationsListRequest.builder()
                    .excludeArchived(true)
                    .limit(1000)
                    .build()
            );
            
            if (response.isOk()) {
                for (Conversation channel : response.getChannels()) {
                    if (BOT_CHANNEL.equals(channel.getName())) {
                        return channel.getId();
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            log.error("Error finding #ethbot channel", e);
            return null;
        }
    }

    /**
     * Post message to #ethbot channel
     */
    private void postToEthBot(String channelId, String message) {
        try {
            MethodsClient client = slack.methods(slackConfig.botToken());
            client.chatPostMessage(req -> req
                .channel(channelId)
                .text(message)
                .mrkdwn(true)
            );
        } catch (SlackApiException | IOException e) {
            log.error("Failed to post to #ethbot: {}", message, e);
        }
    }

    /**
     * Format number for display
     */
    private String formatNumber(Object value) {
        if (value == null) return "0";
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }
}