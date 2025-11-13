package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.TradeRecommendation;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Quick Recommendation Service
 * <p>
 * Optimized for frequent evaluations (every 15-60 minutes).
 * Uses a single AI call with all context pre-loaded to minimize cost and latency.
 * <p>
 * Cost comparison vs. full analysis:
 * - Full analysis: ~3,000-4,000 tokens, 5-6 API calls
 * - Quick recommend: ~1,500-2,000 tokens, 1 API call
 * - Savings: ~60-70% cost reduction
 * <p>
 * Use this for:
 * - Automated periodic checks
 * - Quick decision-making
 * - High-frequency monitoring
 * <p>
 * Use full analysis for:
 * - Detailed investigation
 * - Complex market conditions
 * - Manual decision support
 */
@Service
@Slf4j
public class QuickRecommendationService {

    private final ChatModel chatModel;
    private final TradingContextService tradingContextService;
    private final RecommendationPersistenceService persistenceService;

    private static final String QUICK_RECOMMENDATION_PROMPT = """
            You are an expert cryptocurrency day-trading analyst. Analyze and produce an execution-ready plan for ETH/USDC on Binance spot only.
            
            ===== CURRENT MARKET DATA =====
            {marketData}
            
            ===== TECHNICAL INDICATORS (5m) =====
            {technical5m}
            
            ===== TECHNICAL INDICATORS (15m) =====
            {technical15m}
            
            ===== TECHNICAL INDICATORS (1h) =====
            {technical1h}
            
            ===== MARKET SENTIMENT =====
            {sentiment}
            
            ===== YOUR PORTFOLIO =====
            {portfolio}
            
            ===== TRADING MEMORY =====
            {tradingMemory}
            
            ===== GUIDELINES (ETH/USDC DAY-TRADING) =====
            - Instrument & venue: ETH/USDC on Binance spot ONLY.
            - Timeframes: 5m = ENTRY timing, 15m = CONFIRMATION, 1h = DIRECTIONAL BIAS.
              Enter only when 5m aligns with 15m OR when a valid countertrend mean-reversion setup exists.
            - Regime detection: classify Trending vs Ranging (e.g., ADX/DI, BB width/ATR).
              • Trending: favor pullback entries to EMA/VWAP with continuation targets (ADX>~25 helpful).
              • Ranging: favor mean reversion (RSI divergences, bands/VWAP reversion). Avoid chasing breakouts without volume.
            - Execution plan (required for BUY/SELL):
              • Entry type (MARKET/LIMIT) and price zone.
              • Stop-loss at clear invalidation level.
              • Take-profit targets: at least TP1 around 1R; optional TP2 (2–3R) or trailing logic.
            - Position sizing (high risk tolerance): size by risk, not only by % of portfolio.
              • risk_per_trade = 3–5% of account. amount(USD) ≈ min(risk_per_trade / stop_distance(USD), portfolio cap).
              • Prefer USD amounts; round USD to 2 decimals (ETH to 5 decimals if used).
            - Fees/slippage/spread: HOLD if spread is wide or expected edge < fees+slippage. Do not scalp tiny moves with poor liquidity.
            - Volume/VWAP: breakouts should have volume expansion; VWAP (anchored to day open) is a key reference for both regimes.
            - Cooldown: minimum 10–15 minutes after taking a trade or flipping direction to reduce churn; max ~4 trades/hour.
            - Evaluation cadence: automated evaluation runs hourly to control AI cost. Prefer scheduling the next check at the next hourly cycle, unless a strong immediate risk/momentum trigger warrants earlier manual review.
            - Data recency: if any data block appears older than ~120 seconds, HOLD and request refresh.
            - Sentiment: use as tiebreaker only; never enter on sentiment alone.
            - Confidence model:
              • HIGH: 3+ quality signals agree with regime and volume confirms.
              • MEDIUM: 2 signals agree; acceptable alignment.
              • LOW: conflicting signals → prefer HOLD.
            - Output constraints:
              • Set amount ONLY for BUY/SELL; use NONE for HOLD.
              • Provide 2–4 sentence reasoning citing concrete indicators/levels and regime.
              • Memory (max 3 bullets):
                1) Current thesis/trigger (e.g., "5m pullback to VWAP with rising volume")
                2) Invalidation (e.g., "Close below VWAP and 15m EMA = exit/avoid")
                3) Next check (e.g., "Re-evaluate next hourly cycle"; if proposing an earlier check, specify an explicit strong trigger like "only if 15m momentum flips with volume expansion or SL at risk")
            
            Be decisive but risk-aware. Quality over quantity; HOLD if uncertain.
            
            {format}
            """;

    public QuickRecommendationService(ChatModel chatModel,
                                     TradingContextService tradingContextService,
                                     RecommendationPersistenceService persistenceService) {
        this.chatModel = chatModel;
        this.tradingContextService = tradingContextService;
        this.persistenceService = persistenceService;
    }

    /**
     * Get quick trading recommendation with all context pre-loaded
     * Returns structured, type-safe recommendation using Spring AI's BeanOutputConverter
     * Optimized for cost and speed
     */
    public TradeRecommendation getQuickRecommendation(String username) {
        try {
            log.info("Generating quick recommendation for user: {}", username);
            long startTime = System.currentTimeMillis();

            // Gather all context using centralized service
            Map<String, String> contextData = tradingContextService.formatForPrompt();
            
            // Setup Spring AI structured output converter
            BeanOutputConverter<TradeRecommendation> outputConverter = 
                new BeanOutputConverter<>(TradeRecommendation.class);
            
            // Convert to Map<String, Object> for PromptTemplate
            Map<String, Object> context = new HashMap<>(contextData);
            context.put("format", outputConverter.getFormat());

            // Single AI call with all context + JSON schema
            PromptTemplate promptTemplate = new PromptTemplate(QUICK_RECOMMENDATION_PROMPT);
            Prompt prompt = promptTemplate.create(context);
            
            String response = chatModel.call(prompt).getResult().getOutput().getContent();
            log.debug("Raw AI response: {}", response);
            
            // Parse structured output
            TradeRecommendation recommendation = outputConverter.convert(response);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Quick recommendation generated in {}ms: {} {} (confidence: {})", 
                duration, recommendation.signal(), 
                recommendation.amount() != null ? recommendation.amount() + " " + recommendation.amountType() : "NONE",
                recommendation.confidence());
            
            // Persist recommendation for future memory/context
            try {
                persistenceService.saveRecommendation(recommendation);
                log.debug("Recommendation persisted with memory: {}", 
                    recommendation.memory() != null ? recommendation.memory().size() + " items" : "none");
            } catch (Exception e) {
                log.error("Failed to persist recommendation (non-critical)", e);
                // Don't fail the request if persistence fails
            }

            return recommendation;

        } catch (Exception e) {
            log.error("Error generating quick recommendation", e);
            throw new RuntimeException("Failed to generate recommendation: " + e.getMessage(), e);
        }
    }
}
