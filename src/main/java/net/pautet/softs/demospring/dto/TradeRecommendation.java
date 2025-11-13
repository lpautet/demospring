package net.pautet.softs.demospring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI-Generated Trade Recommendation
 * 
 * This record is used with Spring AI's structured output feature to get
 * type-safe, validated trading recommendations from the LLM.
 * 
 * Spring AI will generate a JSON schema from this class and include it in the prompt,
 * ensuring the LLM returns data in exactly this format.
 * 
 * NEW: Includes AI working memory for stateful recommendations across time.
 */
public record TradeRecommendation(
    
    @JsonProperty(required = true)
    @JsonPropertyDescription("Trading signal: BUY to enter position, SELL to exit, HOLD to wait")
    Signal signal,
    
    @JsonProperty(required = true)
    @JsonPropertyDescription("Confidence level based on indicator agreement: HIGH (3+ agree), MEDIUM (2 agree), LOW (conflicting)")
    Confidence confidence,
    
    @JsonProperty(required = false)
    @JsonPropertyDescription("Recommended trade amount as a number (e.g., 100.50 for USD or 0.05 for ETH). Null if signal is HOLD.")
    BigDecimal amount,
    
    @JsonProperty(required = true)
    AmountType amountType,
    
    @JsonProperty(required = true)
    @JsonPropertyDescription("Brief 2-4 sentence explanation of the key factors driving this recommendation. Focus on technical signals, sentiment, and risk management.")
    String reasoning,

    @JsonProperty(required = false)
    @JsonPropertyDescription("""
        Your working memory to carry forward (max 3 concise bullet points):
        1. Key patterns or hypotheses you're currently tracking
        2. Important price levels, conditions, or triggers to monitor
        3. What would invalidate or change your current thesis
        Keep each point under 15 words. This helps maintain consistency across analyses.
        """)
    List<String> memory,

    @JsonProperty(required = false)
    @JsonPropertyDescription("Preferred entry type: MARKET for immediate execution or LIMIT for specified price.")
    EntryType entryType,

    @JsonProperty(required = false)
    @JsonPropertyDescription("Entry price for LIMIT orders. Null for MARKET entries.")
    BigDecimal entryPrice,

    @JsonProperty(required = false)
    @JsonPropertyDescription("Proposed stop-loss price. Null for HOLD.")
    BigDecimal stopLoss,

    @JsonProperty(required = false)
    @JsonPropertyDescription("First take-profit target price. Null for HOLD.")
    BigDecimal takeProfit1,

    @JsonProperty(required = false)
    @JsonPropertyDescription("Second (runner) take-profit or trailing reference price. Null if not applicable.")
    BigDecimal takeProfit2,

    @JsonProperty(required = false)
    @JsonPropertyDescription("Intended holding period in minutes for this intraday setup (e.g., 10-120). Null for HOLD.")
    Integer timeHorizonMinutes
) {
    
    /**
     * Trading signal direction
     */
    public enum Signal {
        BUY,    // Enter long position
        SELL,   // Exit position or short
        HOLD    // Wait for better opportunity
    }
    
    /**
     * Confidence level in the recommendation
     */
    public enum Confidence {
        HIGH,    // 3+ indicators agree, strong signal
        MEDIUM,  // 2 indicators agree, moderate signal
        LOW      // Conflicting signals, uncertain
    }
    
    /**
     * Type of trade amount
     */
    public enum AmountType {
        USD,   // Dollar amount (e.g., $100)
        ETH,   // ETH amount (e.g., 0.05 ETH)
        NONE   // No trade (HOLD signal)
    }

    /** Entry type for execution */
    public enum EntryType {
        MARKET,
        LIMIT
    }
    
    /**
     * Check if this recommendation is actionable (BUY or SELL with amount)
     */
    public boolean isActionable() {
        return (signal == Signal.BUY || signal == Signal.SELL) 
            && amount != null 
            && amount.compareTo(BigDecimal.ZERO) > 0
            && amountType != AmountType.NONE;
    }
    
    /**
     * Check if confidence is high enough to execute automatically
     */
    public boolean isHighConfidence() {
        return confidence == Confidence.HIGH;
    }
    
    /**
     * Format for human-readable display (Slack, logs)
     */
    public String toDisplayString() {
        String amountStr = amount != null 
            ? (amountType == AmountType.USD 
                ? String.format("$%.2f", amount) 
                : String.format("%.5f ETH", amount))
            : "NONE";

        String sl = stopLoss != null ? String.format("SL: %.2f", stopLoss) : "SL: -";
        String tp1 = takeProfit1 != null ? String.format("TP1: %.2f", takeProfit1) : "TP1: -";
        String tp2 = takeProfit2 != null ? String.format("TP2: %.2f", takeProfit2) : "TP2: -";
        String et = entryType != null
            ? ("ENTRY: " + entryType + (entryPrice != null ? String.format(" @ $%.2f", entryPrice) : ""))
            : "ENTRY: -";
        String th = timeHorizonMinutes != null ? ("HORIZON: " + timeHorizonMinutes + "m") : "HORIZON: -";
            
        return String.format("""
            SIGNAL: %s
            CONFIDENCE: %s
            AMOUNT: %s
            %s | %s | %s
            %s | %s
            
            REASONING:
            %s
            """, signal, confidence, amountStr, sl, tp1, tp2, et, th, reasoning);
    }
    
    /**
     * Format for Slack with emojis and formatting
     */
    public String toSlackFormat() {
        String signalEmoji = switch (signal) {
            case BUY -> "🟢";
            case SELL -> "🔴";
            case HOLD -> "⏸️";
        };
        
        String confidenceEmoji = switch (confidence) {
            case HIGH -> "🔥";
            case MEDIUM -> "⚡";
            case LOW -> "💭";
        };
        
        String amountStr = amount != null 
            ? (amountType == AmountType.USD 
                ? String.format("$%.2f", amount) 
                : String.format("%.5f ETH", amount))
            : "_None_";
        String sl = stopLoss != null ? String.format("SL: %.2f", stopLoss) : "SL: -";
        String tp1 = takeProfit1 != null ? String.format("TP1: %.2f", takeProfit1) : "TP1: -";
        String tp2 = takeProfit2 != null ? String.format("TP2: %.2f", takeProfit2) : "TP2: -";
        String et = entryType != null ? ("ENTRY: " + entryType) : "ENTRY: -";
        String th = timeHorizonMinutes != null ? ("HORIZON: " + timeHorizonMinutes + "m") : "HORIZON: -";
        
        return String.format("""
            *Signal:* %s *%s*
            *Confidence:* %s *%s*
            *Amount:* %s
            %s | %s | %s  
            %s | %s
            
            *Reasoning:*
            %s
            """, signalEmoji, signal, confidenceEmoji, confidence, amountStr, sl, tp1, tp2, et, th, reasoning);
    }
}
