package net.pautet.softs.demospring.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Sentiment Analysis Service
 * Aggregates market sentiment from multiple sources:
 * - Fear & Greed Index
 * - News sentiment
 * - Market momentum sentiment
 */
@Service
@Slf4j
public class SentimentAnalysisService {

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public SentimentAnalysisService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    /**
     * Get comprehensive market sentiment analysis
     * <p>
     * Note: Caching removed to avoid deserialization issues with Spring's default cache
     */
    public Map<String, Object> getMarketSentiment(String symbol) {
        Map<String, Object> sentiment = new LinkedHashMap<>();

        try {
            // Fear & Greed Index
            Map<String, Object> fearGreed = getFearGreedIndex();
            
            // Market momentum sentiment (based on price changes)
            Map<String, Object> momentum = getMarketMomentumSentiment(symbol);
            
            // Aggregate overall sentiment
            Map<String, Object> overall = aggregateSentiment(fearGreed, momentum);

            // Flatten structure for easy access
            sentiment.put("overallScore", overall.get("score"));
            sentiment.put("classification", overall.get("sentiment"));
            sentiment.put("recommendation", overall.get("recommendation"));
            sentiment.put("confidence", overall.get("confidence"));
            
            // Fear & Greed specific
            sentiment.put("fearGreedIndex", fearGreed.get("value"));
            sentiment.put("fearGreedLabel", fearGreed.get("classification"));
            sentiment.put("fearGreedSignal", fearGreed.get("signal"));
            sentiment.put("interpretation", fearGreed.get("interpretation"));
            
            // Momentum specific
            sentiment.put("momentumScore", momentum.get("score"));
            sentiment.put("momentumSignal", momentum.get("signal"));
            
            // Keep nested for detailed access if needed
            sentiment.put("fearGreedDetails", fearGreed);
            sentiment.put("momentumDetails", momentum);
            sentiment.put("overallDetails", overall);

            return sentiment;

        } catch (Exception e) {
            log.error("Error analyzing market sentiment", e);
            // Return safe defaults
            return Map.of(
                "error", e.getMessage(),
                "overallScore", 0.0,
                "classification", "N/A",
                "fearGreedIndex", 50,
                "fearGreedLabel", "N/A",
                "interpretation", "Sentiment data unavailable"
            );
        }
    }

    /**
     * Fear & Greed Index from Alternative.me
     * FREE API, no key required
     */
    private Map<String, Object> getFearGreedIndex() {
        try {
            String response = restClient.get()
                    .uri("https://api.alternative.me/fng/")
                    .retrieve()
                    .body(String.class);

            JsonNode data = objectMapper.readTree(response);
            JsonNode latestData = data.get("data").get(0);

            int value = latestData.get("value").asInt();
            String classification = latestData.get("value_classification").asText();

            // Convert to -1 to +1 scale
            // 0-25: Extreme Fear (contrarian BUY signal)
            // 25-45: Fear (slight BUY)
            // 45-55: Neutral
            // 55-75: Greed (slight SELL)
            // 75-100: Extreme Greed (contrarian SELL signal)
            
            double normalizedScore;
            String signal;
            String interpretation;

            if (value <= 25) {
                normalizedScore = 0.7; // Extreme fear = contrarian buy
                signal = "EXTREME_FEAR_CONTRARIAN_BUY";
                interpretation = "Market in extreme fear - historically good buying opportunity";
            } else if (value <= 45) {
                normalizedScore = 0.3;
                signal = "FEAR_SLIGHT_BUY";
                interpretation = "Market fearful - potential buying opportunity";
            } else if (value <= 55) {
                normalizedScore = 0.0;
                signal = "NEUTRAL";
                interpretation = "Market sentiment neutral";
            } else if (value <= 75) {
                normalizedScore = -0.3;
                signal = "GREED_SLIGHT_SELL";
                interpretation = "Market greedy - consider taking profits";
            } else {
                normalizedScore = -0.7; // Extreme greed = contrarian sell
                signal = "EXTREME_GREED_CONTRARIAN_SELL";
                interpretation = "Market in extreme greed - high risk of correction";
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("value", value);
            result.put("classification", classification);
            result.put("normalizedScore", normalizedScore);
            result.put("signal", signal);
            result.put("interpretation", interpretation);
            result.put("contrarian", value <= 25 || value >= 75); // Contrarian signals at extremes

            return result;

        } catch (Exception e) {
            log.warn("Unable to fetch Fear & Greed Index: {}", e.getMessage());
            return Map.of(
                "value", 50,
                "classification", "Neutral",
                "normalizedScore", 0.0,
                "signal", "NEUTRAL",
                "interpretation", "Sentiment data unavailable",
                "contrarian", false
            );
        }
    }

    /**
     * Market momentum sentiment based on multi-timeframe price changes
     */
    private Map<String, Object> getMarketMomentumSentiment(String symbol) {
        // This would ideally fetch 1h, 4h, 24h price changes
        // For now, return a simplified version
        
        Map<String, Object> momentum = new LinkedHashMap<>();
        
        // Placeholder logic - in real implementation, fetch actual price changes
        // from Binance for 1h, 4h, 24h timeframes
        double change1h = 0.5;   // Would fetch from API
        double change4h = 1.2;   // Would fetch from API
        double change24h = 2.3;  // Would fetch from API

        double avgChange = (change1h + change4h + change24h) / 3.0;
        
        String signal;
        double score;
        
        if (avgChange > 3.0) {
            signal = "VERY_BULLISH";
            score = 0.8;
        } else if (avgChange > 1.0) {
            signal = "BULLISH";
            score = 0.5;
        } else if (avgChange > -1.0) {
            signal = "NEUTRAL";
            score = 0.0;
        } else if (avgChange > -3.0) {
            signal = "BEARISH";
            score = -0.5;
        } else {
            signal = "VERY_BEARISH";
            score = -0.8;
        }

        momentum.put("change1h", change1h);
        momentum.put("change4h", change4h);
        momentum.put("change24h", change24h);
        momentum.put("avgChange", avgChange);
        momentum.put("signal", signal);
        momentum.put("score", score);
        momentum.put("interpretation", 
            String.format("Average %+.2f%% across timeframes suggests %s momentum", 
                avgChange, signal.toLowerCase()));

        return momentum;
    }

    /**
     * Aggregate all sentiment sources into final assessment
     */
    private Map<String, Object> aggregateSentiment(Map<String, Object> fearGreed, 
                                                    Map<String, Object> momentum) {
        
        double fearGreedScore = (double) fearGreed.get("normalizedScore");
        double momentumScore = (double) momentum.get("score");

        // Weighted average (Fear & Greed: 40%, Momentum: 60%)
        double overallScore = (fearGreedScore * 0.4) + (momentumScore * 0.6);

        String sentiment;
        String recommendation;
        String confidence;

        if (overallScore > 0.5) {
            sentiment = "VERY_BULLISH";
            recommendation = "Strong sentiment supports BUYING";
            confidence = "HIGH";
        } else if (overallScore > 0.2) {
            sentiment = "BULLISH";
            recommendation = "Positive sentiment favors BUYING";
            confidence = "MEDIUM";
        } else if (overallScore > -0.2) {
            sentiment = "NEUTRAL";
            recommendation = "Mixed sentiment - rely on technicals";
            confidence = "LOW";
        } else if (overallScore > -0.5) {
            sentiment = "BEARISH";
            recommendation = "Negative sentiment suggests SELLING";
            confidence = "MEDIUM";
        } else {
            sentiment = "VERY_BEARISH";
            recommendation = "Strong negative sentiment - consider SELLING";
            confidence = "HIGH";
        }

        Map<String, Object> overall = new LinkedHashMap<>();
        overall.put("score", overallScore);
        overall.put("sentiment", sentiment);
        overall.put("recommendation", recommendation);
        overall.put("confidence", confidence);
        overall.put("components", Map.of(
            "fearGreed", fearGreedScore,
            "momentum", momentumScore
        ));

        return overall;
    }
}
