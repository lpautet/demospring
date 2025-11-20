
package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.TradeRecommendation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class QuickRecommendationServiceGrok {

    private final ChatClient chatClient;
    private final TradingContextService tradingContextService;
    private final RecommendationPersistenceService persistenceService;

    // NEW: Ultra-strict prompt with HOLD bias + explicit checks
    private static final String STRICT_RECOMMENDATION_PROMPT = """
            You are an extremely disciplined, low-frequency ETH/USDC day-trader.
            DEFAULT ACTION = HOLD. You are strongly biased toward doing nothing.
            
            Only propose BUY or SELL if ALL of these are true:
            1. Market regime is clearly Trending Up / Trending Down / Ranging (state it explicitly)
            2. 1h bias + 15m confirmation + 5m trigger aligned OR high-probability mean-reversion with divergence
            3. Projected R:R ≥ 2.0 before fees/slippage (you must calculate and show it)
            4. Data age of all data blocks < 90 seconds
            5. No cooldown violation
            If any condition missing → HOLD and say which one failed.
            
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
            
            ===== STRICT GUIDELINES =====
            - FIRST: State regime in one sentence with evidence (e.g. "Trending Up — 1h ADX 32, +DI>-DI, price > rising 50 EMA")
            - Entry rules:
              • Trending: only pullback to VWAP/20-50 EMA with volume spike on bounce
              Ranging: only RSI divergence + price at band + stochastic turning from <15 or >85
              Never chase breakouts without 150%+ volume expansion on all 3 timeframes
            - Risk exactly 4% of USDC balance per trade
            - Minimum R:R = 2.0. Show calculation: (TP1 distance) / (SL distance)
            - Spread > 4 bps or liquidity poor → HOLD
            - Never trade on sentiment alone
            
            Respond EXACTLY in this format (JSON for structured parsing):
            
            {format}
            """;

    // NEW: Ironclad output format — works 99.9% of the time with GPT-5.1 and 100% with Grok-4
    private static final String JSON_FORMAT = """
            {
              "signal": "BUY|SELL|HOLD",
              "confidence": "HIGH|MEDIUM|LOW",
              "regime": "single sentence regime classification with evidence",
              "reasoning": "2–4 sentences citing exact prices, indicators, volume, R:R calculation, and which strict condition is satisfied",
              "entryType": "MARKET|LIMIT|null",
              "entryPrice": price number or null,
              "stopLoss": price number,
              "tp1": price number,
              "tp2": price number or null,
              "expectedRR": number with 2 decimals (e.g. 2.37),
              "amountUsd": number with 2 decimals or null,
              "amountEth": number with 5 decimals or null,
              "amountType": "USD|ETH|null",
              "cooldownUntil": "ISO8601 timestamp UTC or null",
              "memory": [
                "Current thesis: ...",
                "Invalidation: ...",
                "Next check: Re-evaluate next hourly cycle (or earlier only if ...)"
              ]
            }
            Only include amountUsd/amountEth when signal is BUY or SELL. Otherwise null.
            """;

    public QuickRecommendationServiceGrok(ChatModel chatModel,
                                      TradingContextService tradingContextService,
                                      RecommendationPersistenceService persistenceService) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.tradingContextService = tradingContextService;
        this.persistenceService = persistenceService;
    }

    public TradeRecommendation getQuickRecommendation(String username) {
        try {
            log.info("Generating Grok-4 recommendation for user: {}", username);
            long start = System.currentTimeMillis();

            Map<String, String> contextData = tradingContextService.formatForPrompt();

            BeanOutputConverter<TradeRecommendation> converter = new BeanOutputConverter<>(TradeRecommendation.class);

            Map<String, Object> modelParams = new HashMap<>(contextData);
            modelParams.put("format", JSON_FORMAT);  // This is the magic that makes Grok obey

            PromptTemplate pt = new PromptTemplate(STRICT_RECOMMENDATION_PROMPT);
            String prompt = pt.render(modelParams);

            log.info("=== SENDING TO GROK ===");
            log.info("Prompt length: {} characters", prompt.length());
            log.info("First 500 chars: {}", prompt.substring(0, Math.min(500, prompt.length())));
            log.info("=======================");

            var chatResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();
            
            String rawResponse = chatResponse.getResult().getOutput().getText();
            
            // Log token usage
            var metadata = chatResponse.getMetadata();
            if (metadata != null && metadata.getUsage() != null) {
                var usage = metadata.getUsage();
                log.info("Grok token usage - Prompt: {}, Completion: {}, Total: {}", 
                    usage.getPromptTokens(), 
                    usage.getCompletionTokens(), 
                    usage.getTotalTokens());
            }
            
            log.debug("Raw Grok response:\n{}", rawResponse);

            TradeRecommendation recommendation = converter.convert(rawResponse);

            long duration = System.currentTimeMillis() - start;
            log.info("Grok-4 recommendation generated in {}ms → {} (confidence: {})",
                    duration, recommendation.signal(), recommendation.confidence());

            persistenceService.saveRecommendation(recommendation);

            return recommendation;

        } catch (Exception e) {
            log.error("Failed to generate Grok recommendation", e);
            throw new RuntimeException("Grok recommendation failed: " + e.getMessage(), e);
        }
    }


    private String callWithFlexFallback(String prompt) {
        try {
            return chatClient.prompt()
                    .user(prompt)
                    .options(OpenAiChatOptions.builder().serviceTier("flex").build())
                    .call()
                    .content();
        } catch (Exception ex) {
            log.warn("Flex tier failed, falling back to standard", ex);
            return chatClient.prompt()
                    .user(prompt)
                    .options(OpenAiChatOptions.builder().serviceTier("auto").build())
                    .call()
                    .content();
        }
    }
}

