package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.TradeRecommendation;
import net.pautet.softs.demospring.entity.RecommendationHistory;
import net.pautet.softs.demospring.repository.RecommendationHistoryRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Trading Memory Service
 * <p>
 * Builds compressed, contextual summaries of trading history for AI:
 * - AI's previous working memory
 * - Recent significant events (position changes)
 * - Performance feedback
 * - Pattern recognition
 * <p>
 * Token-efficient: ~200-250 tokens vs ~2000+ for full history
 */
@Service
@Slf4j
public class TradingMemoryService {
    
    private final RecommendationHistoryRepository repository;

    public TradingMemoryService(RecommendationHistoryRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Get full trading memory context for AI prompt
     * Returns compressed summary of last 24h activity
     */
    public String getTradingMemoryContext() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            
            // Get AI's most recent memory
            String aiMemory = getAIMemory();
            
            // Get significant events (position changes, executions)
            String recentActivity = getRecentActivity(cutoff);
            
            // Get performance feedback
            String performance = getPerformanceSummary(cutoff);
            
            // Build compact summary
            return String.format("""
                    📊 TRADING MEMORY (Last 24h)
                    
                    Your Previous Memory:
                    %s
                    
                    Recent Activity:
                    %s
                    
                    Performance Context:
                    %s
                    
                    Note: Use this memory to maintain consistency. Only deviate if conditions significantly changed.
                    """, aiMemory, recentActivity, performance);
                    
        } catch (Exception e) {
            log.error("Error building trading memory", e);
            return "No previous trading memory available (first analysis or error).";
        }
    }
    
    /**
     * Get AI's previous working memory
     */
    private String getAIMemory() {
        var lastRec = repository.findFirstByOrderByTimestampDesc();
        
        if (lastRec.isEmpty() || lastRec.get().getAiMemory() == null || lastRec.get().getAiMemory().isEmpty()) {
            return "_No previous memory_ (First analysis)";
        }
        
        RecommendationHistory last = lastRec.get();
        Duration age = Duration.between(last.getTimestamp(), LocalDateTime.now());
        String ageStr = formatDuration(age);
        
        String memory = last.getAiMemory().stream()
            .map(m -> "  • " + m)
            .collect(Collectors.joining("\n"));
            
        return String.format("[%s ago]\n%s", ageStr, memory);
    }
    
    /**
     * Get recent significant activity
     * Only shows position changes and executions (not every HOLD)
     */
    private String getRecentActivity(LocalDateTime since) {
        List<RecommendationHistory> recent = repository.findRecentSince(since);
        
        if (recent.isEmpty()) {
            return "_No recent activity_";
        }
        
        // Filter to significant events only
        List<RecommendationHistory> significant = filterSignificantEvents(recent);
        
        if (significant.isEmpty()) {
            // All HOLDs
            long holdCount = recent.stream()
                .filter(r -> r.getSignal() == TradeRecommendation.Signal.HOLD)
                .count();
            return String.format("_Consistent HOLD signal_ (%d recommendations, no position changes)", holdCount);
        }
        
        // Show last 3 significant events
        return significant.stream()
            .limit(3)
            .map(this::formatEvent)
            .collect(Collectors.joining("\n"));
    }
    
    /**
     * Filter to significant events (signal changes or executions)
     */
    private List<RecommendationHistory> filterSignificantEvents(List<RecommendationHistory> all) {
        if (all.isEmpty()) return List.of();
        
        // Track previous signal to detect changes
        TradeRecommendation.Signal prevSignal = null;
        return all.stream()
            .filter(r -> {
                boolean isSignificant = r.getExecuted() || 
                                       (prevSignal != null && prevSignal != r.getSignal());
                // Update prevSignal for next iteration (note: this modifies closure state)
                return isSignificant;
            })
            .toList();
    }
    
    /**
     * Format a single event for display
     */
    private String formatEvent(RecommendationHistory rec) {
        Duration age = Duration.between(rec.getTimestamp(), LocalDateTime.now());
        String timeStr = formatDuration(age) + " ago";
        String execMark = rec.getExecuted() ? " ✅" : "";
        
        // Compact one-liner
        return String.format("  • [%s] %s%s - %s", 
            timeStr,
            rec.getSignal(),
            execMark,
            truncate(rec.getReasoning(), 60)
        );
    }
    
    /**
     * Get performance summary
     */
    private String getPerformanceSummary(LocalDateTime since) {
        List<RecommendationHistory> executed = repository.findExecutedRecommendations();
        
        if (executed.isEmpty()) {
            return "_No trades executed yet_";
        }
        
        // Count recent executions
        long recentTrades = executed.stream()
            .filter(r -> r.getTimestamp().isAfter(since))
            .count();
            
        if (recentTrades == 0) {
            return String.format("_No trades in last 24h_ (Total: %d historical trades)", executed.size());
        }
        
        return String.format("%d trades executed in last 24h", recentTrades);
    }
    
    /**
     * Format duration to human-readable string
     */
    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        
        if (hours > 0) {
            return String.format("%dh%dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }
    
    /**
     * Truncate string to max length
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * Check if memory should be reset (e.g., too old)
     */
    public boolean shouldResetMemory() {
        var lastRec = repository.findFirstByOrderByTimestampDesc();
        
        if (lastRec.isEmpty()) {
            return false; // No memory to reset
        }
        
        Duration age = Duration.between(lastRec.get().getTimestamp(), LocalDateTime.now());
        
        // Reset if more than 48 hours old
        return age.toHours() > 48;
    }
    
    /**
     * Get cooldown information for trading context
     * Returns human-readable cooldown status
     */
    public String getCooldownInfo() {
        try {
            var lastRec = repository.findFirstByOrderByTimestampDesc();
            
            if (lastRec.isEmpty()) {
                return "No recent trades (no cooldown)";
            }
            
            RecommendationHistory last = lastRec.get();
            
            // Check if there's an explicit cooldown timestamp
            if (last.getCooldownUntil() != null) {
                LocalDateTime cooldownEnd = last.getCooldownUntil();
                LocalDateTime now = LocalDateTime.now();
                
                if (now.isBefore(cooldownEnd)) {
                    long minutesLeft = Duration.between(now, cooldownEnd).toMinutes();
                    return String.format("COOLDOWN ACTIVE until %s (%d min left)", 
                        cooldownEnd.toString(), minutesLeft);
                }
            }
            
            // Otherwise check time since last trade
            Duration timeSinceLast = Duration.between(last.getTimestamp(), LocalDateTime.now());
            long minutesSince = timeSinceLast.toMinutes();
            
            if (minutesSince < 15) {
                return String.format("Last trade %d min ago (min 15 min between trades)", minutesSince);
            }
            
            return String.format("No cooldown (last trade %d min ago)", minutesSince);
            
        } catch (Exception e) {
            log.warn("Failed to get cooldown info", e);
            return "Cooldown status unknown";
        }
    }
}
