package net.pautet.softs.demospring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;
import java.util.List;

public record TradeRecommendation(

        @JsonProperty(required = true)
        @JsonPropertyDescription("Trading signal: BUY, SELL or HOLD")
        Signal signal,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Confidence: HIGH, MEDIUM or LOW")
        Confidence confidence,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Expected R:R >= 2.00 for trades, null for HOLD")
        BigDecimal expectedRR,

        @JsonProperty(required = true)
        @JsonPropertyDescription("One-sentence regime with evidence")
        String regime,

        @JsonProperty(required = false)
        @JsonPropertyDescription("USD amount when signal is BUY/SELL, null otherwise")
        BigDecimal amountUsd,

        @JsonProperty(required = false)
        @JsonPropertyDescription("ETH amount (rarely used), null otherwise")
        BigDecimal amountEth,

        @JsonProperty(required = true)
        @JsonPropertyDescription("USD, ETH or NONE")
        AmountType amountType,

        @JsonProperty(required = true)
        @JsonPropertyDescription("2–4 sentences reasoning with prices, R:R calc, etc.")
        String reasoning,

        @JsonProperty(required = false)
        EntryType entryType,

        @JsonProperty(required = false)
        BigDecimal entryPrice,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Stop-loss price — REQUIRED for BUY/SELL")
        BigDecimal stopLoss,

        @JsonProperty(required = false)
        @JsonPropertyDescription("First take-profit — REQUIRED for BUY/SELL")
        BigDecimal tp1,

        @JsonProperty(required = false)
        BigDecimal tp2,

        @JsonProperty(required = false)
        @JsonPropertyDescription("ISO-8601 UTC cooldown end, e.g. 2025-11-19T14:25:00Z")
        String cooldownUntil,

        // FIXED: @JsonProperty was missing!
        @JsonProperty(required = false)
        @JsonPropertyDescription("Exactly 3 memory bullets: 1) Thesis 2) Invalidation 3) Next check")
        List<String> memory

) {

    public enum Signal { BUY, SELL, HOLD }
    public enum Confidence { HIGH, MEDIUM, LOW }
    public enum AmountType { USD, ETH, NONE }
    public enum EntryType { MARKET, LIMIT }

    // Convenience
    public BigDecimal amount() {
        return amountUsd != null ? amountUsd : amountEth;
    }

    public boolean isActionable() {
        return (signal == Signal.BUY || signal == Signal.SELL)
                && amountUsd != null && amountUsd.compareTo(BigDecimal.ZERO) > 0
                && stopLoss != null && tp1 != null
                && expectedRR != null && expectedRR.compareTo(BigDecimal.valueOf(2.0)) >= 0;
    }
    
    /**
     * Format for human-readable display (logs, console)
     */
    public String toDisplayString() {
        String amountStr = amountUsd != null 
            ? String.format("$%.2f", amountUsd) 
            : (amountEth != null ? String.format("%.5f ETH", amountEth) : "NONE");

        String sl = stopLoss != null ? String.format("SL: $%.2f", stopLoss) : "SL: -";
        String tp1Str = tp1 != null ? String.format("TP1: $%.2f", tp1) : "TP1: -";
        String tp2Str = tp2 != null ? String.format("TP2: $%.2f", tp2) : "TP2: -";
        String rrStr = expectedRR != null ? String.format("R:R: %.2f", expectedRR) : "R:R: -";
        String et = entryType != null
            ? ("ENTRY: " + entryType + (entryPrice != null ? String.format(" @ $%.2f", entryPrice) : ""))
            : "ENTRY: -";
            
        return String.format("""
            SIGNAL: %s
            CONFIDENCE: %s
            REGIME: %s
            AMOUNT: %s
            %s | %s | %s | %s
            %s
            
            REASONING:
            %s
            """, signal, confidence, regime != null ? regime : "N/A", amountStr, 
            sl, tp1Str, tp2Str, rrStr, et, reasoning);
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
        
        String amountStr = amountUsd != null 
            ? String.format("$%.2f", amountUsd) 
            : (amountEth != null ? String.format("%.5f ETH", amountEth) : "_None_");
        
        String sl = stopLoss != null ? String.format("SL: $%.2f", stopLoss) : "SL: -";
        String tp1Str = tp1 != null ? String.format("TP1: $%.2f", tp1) : "TP1: -";
        String tp2Str = tp2 != null ? String.format("TP2: $%.2f", tp2) : "TP2: -";
        String rrStr = expectedRR != null ? String.format("R:R: %.2f", expectedRR) : "R:R: -";
        String et = entryType != null ? ("ENTRY: " + entryType) : "ENTRY: -";
        
        return String.format("""
            *Signal:* %s *%s*
            *Confidence:* %s *%s*
            *Regime:* %s
            *Amount:* %s
            %s | %s | %s | %s
            %s
            
            *Reasoning:*
            %s
            """, signalEmoji, signal, confidenceEmoji, confidence, 
            regime != null ? regime : "N/A", amountStr, 
            sl, tp1Str, tp2Str, rrStr, et, reasoning);
    }
}