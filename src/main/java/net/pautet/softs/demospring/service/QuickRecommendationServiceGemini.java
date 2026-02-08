package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.TradeRecommendation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Quick Recommendation Service (Gemini 3 Flash Edition)
 * <p>
 * Optimized for Gemini 3 Flash's high speed and superior instruction following.
 * Uses a rigorous algorithmic checklist for ETH/USDC day trading decisions.
 */
@Service
@Slf4j
public class QuickRecommendationServiceGemini {

    private final ChatClient chatClient;
    private final TradingContextService tradingContextService;
    private final RecommendationPersistenceService persistenceService;

    @Value("classpath:/prompts/gemini-trading.st")
    private Resource tradingPromptResource;

    public QuickRecommendationServiceGemini(@Qualifier("openAiChatModel") ChatModel chatModel,
                                           TradingContextService tradingContextService,
                                           RecommendationPersistenceService persistenceService) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.tradingContextService = tradingContextService;
        this.persistenceService = persistenceService;
    }

    /**
     * Generate quick trading recommendation using Gemini
     */
    public TradeRecommendation getQuickRecommendation(String username) {
        try {
            log.info("Requesting Gemini (Flash) analysis for user: {}", username);
            long startTime = System.currentTimeMillis();

            // 1. Gather Context
            Map<String, String> contextData = tradingContextService.formatForPrompt();
            
            // 2. Prepare Output Converter
            BeanOutputConverter<TradeRecommendation> outputConverter = 
                new BeanOutputConverter<>(TradeRecommendation.class);
            
            Map<String, Object> promptContext = new HashMap<>(contextData);
            promptContext.put("format", outputConverter.getFormat());

            // 3. Render Prompt from external resource
            PromptTemplate promptTemplate = new PromptTemplate(tradingPromptResource);
            String renderedPrompt = promptTemplate.render(promptContext);

            // 4. Call AI
            String response = chatClient.prompt()
                    .user(renderedPrompt)
                    .call()
                    .content();

            log.debug("Gemini response raw: {}", response);
            
            // 5. Parse Response
            TradeRecommendation recommendation = outputConverter.convert(response);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Gemini analysis completed in {}ms: {} (Confidence: {})", 
                duration, recommendation.signal(), recommendation.confidence());
            
            // 6. Persist
            try {
                persistenceService.saveRecommendation(recommendation);
            } catch (Exception e) {
                log.warn("Failed to save Gemini recommendation history", e);
            }

            return recommendation;

        } catch (Exception e) {
            log.error("Error in Gemini recommendation service", e);
            throw new RuntimeException("Gemini analysis failed: " + e.getMessage(), e);
        }
    }
}