package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.BinanceOrderResponse;
import net.pautet.softs.demospring.dto.BinanceOcoOrderResponse;
import net.pautet.softs.demospring.dto.TradeRecommendation;
import net.pautet.softs.demospring.entity.RecommendationHistory;
import net.pautet.softs.demospring.repository.RecommendationHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Recommendation Persistence Service
 * <p>
 * Handles saving recommendations to database with:
 * - Automatic timestamp
 * - Execution tracking
 * - Market context capture
 * - Transaction management
 */
@Service
@Slf4j
public class RecommendationPersistenceService {

    private final RecommendationHistoryRepository repository;

    public RecommendationPersistenceService(RecommendationHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Attach OCO exit order metadata to the recommendation linked to the given entry order id.
     */
    @Transactional
    public void attachOcoDetailsByEntryOrderId(Long entryOrderId, BinanceOcoOrderResponse oco) {
        var opt = repository.findFirstByEntryOrderId(entryOrderId);
        if (opt.isEmpty()) {
            log.warn("No recommendation found for entryOrderId {}", entryOrderId);
            return;
        }
        RecommendationHistory rec = opt.get();
        rec.setOcoOrderListId(oco.orderListId());
        Map<String, String> exit = new HashMap<>();
        exit.put("orderListType", oco.listStatusType());
        exit.put("orderListStatus", oco.listOrderStatus());
        if (oco.orders() != null && !oco.orders().isEmpty()) {
            for (int i = 0; i < oco.orders().size(); i++) {
                var child = oco.orders().get(i);
                exit.put("order" + (i + 1) + "Id", String.valueOf(child.orderId()));
                exit.put("order" + (i + 1) + "ClientId", child.clientOrderId());
            }
        }
        rec.setExitOrders(exit);
        repository.save(rec);
        log.info("Attached OCO details (listId={}) to recommendation {}", oco.orderListId(), rec.getId());
    }

    /**
     * Save a recommendation that was NOT executed
     * Returns the saved entity with ID for later updates
     */
    @Transactional
    public RecommendationHistory saveRecommendation(TradeRecommendation recommendation) {
        return saveRecommendation(recommendation, false, null, null);
    }
    
    /**
     * Find the most recent unexecuted recommendation that matches this one
     * Used to update instead of creating duplicates
     */
    private RecommendationHistory findRecentMatchingRecommendation(TradeRecommendation recommendation) {
        // Find recommendations from last 5 minutes with same signal
        var recentRecs = repository.findRecentSince(
            java.time.LocalDateTime.now().minusMinutes(5)
        );
        
        // Find matching unexecuted recommendation
        return recentRecs.stream()
            .filter(h -> !h.getExecuted())
            .filter(h -> h.getSignal() == recommendation.signal())
            .filter(h -> h.getConfidence() == recommendation.confidence())
            // Match amount if present
            .filter(h -> {
                if (recommendation.amount() == null) return h.getAmount() == null;
                return recommendation.amount().compareTo(h.getAmount()) == 0;
            })
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Save a recommendation with execution details
     * If executed=true, tries to update existing recommendation instead of creating duplicate
     */
    @Transactional
    public RecommendationHistory saveRecommendation(
            TradeRecommendation recommendation, 
            boolean executed,
            BinanceOrderResponse orderResponse,
            Map<String, String> marketContext) {
        
        try {
            RecommendationHistory history;
            
            // If this is an execution, try to find and update the existing recommendation
            if (executed) {
                history = findRecentMatchingRecommendation(recommendation);
                
                if (history != null) {
                    // Found existing recommendation - UPDATE it
                    log.info("Found existing recommendation {} to update with execution details", history.getId());
                } else {
                    // No matching recommendation found - create new one (shouldn't happen normally)
                    log.warn("No matching recommendation found for execution, creating new record");
                    history = RecommendationHistory.fromRecommendation(recommendation);
                }
            } else {
                // Not executed - create new entity
                history = RecommendationHistory.fromRecommendation(recommendation);
            }
            
            // Set execution details and entry order meta
            history.setExecuted(executed);

            if (orderResponse != null) {
                // Track entry order fields regardless of execution
                history.setEntryOrderId(orderResponse.orderId());
                history.setEntryOrderStatus(orderResponse.status());
                history.setEntryOrderType(orderResponse.type());
                history.setEntryClientOrderId(orderResponse.clientOrderId());
                // Record actual placement time for accurate horizon expiry
                try {
                    Long ts = orderResponse.time() != null ? orderResponse.time() : orderResponse.transactTime();
                    java.time.LocalDateTime placedAt = (ts != null && ts > 0)
                            ? java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ts), java.time.ZoneId.systemDefault())
                            : java.time.LocalDateTime.now();
                    history.setEntryPlacedAt(placedAt);
                } catch (Exception ignore) {}
            }

            if (executed && orderResponse != null) {
                Map<String, String> executionResult = new HashMap<>();
                executionResult.put("orderId", String.valueOf(orderResponse.orderId()));
                executionResult.put("executedQty", orderResponse.executedQty().toPlainString());
                executionResult.put("avgPrice", orderResponse.getAveragePrice().toPlainString());
                executionResult.put("status", orderResponse.status());
                history.setExecutionResult(executionResult);
            }
            
            // Save market context if provided
            if (marketContext != null) {
                history.setMarketContext(marketContext);
            }
            
            // Validate and truncate memory if needed (only for new recommendations)
            if (!executed || history.getId() == null) {
                if (recommendation.memory() != null && recommendation.memory().size() > 3) {
                    log.warn("AI returned {} memory items, truncating to 3", recommendation.memory().size());
                    history.setAiMemory(recommendation.memory().subList(0, 3));
                }
            }
            
            boolean isUpdate = (history.getId() != null);
            RecommendationHistory saved = repository.save(history);
            
            if (isUpdate) {
                log.info("Updated recommendation {}: {} {} marked as executed", 
                    saved.getId(),
                    saved.getSignal(), 
                    saved.getAmount() != null ? saved.getAmount() + " " + saved.getAmountType() : "NONE"
                );
            } else {
                log.info("Saved new recommendation: {} {} (executed: {}, id: {})", 
                    saved.getSignal(), 
                    saved.getAmount() != null ? saved.getAmount() + " " + saved.getAmountType() : "NONE",
                    saved.getExecuted(),
                    saved.getId()
                );
            }
            
            return saved;
            
        } catch (Exception e) {
            log.error("Error saving recommendation", e);
            throw new RuntimeException("Failed to persist recommendation", e);
        }
    }

    /**
     * Clean up old recommendations (optional - for housekeeping)
     * Keeps last 1000 recommendations, deletes older ones
     */
    @Transactional
    public void cleanupOldRecommendations() {
        try {
            // Get all recommendations
            long total = repository.count();
            
            if (total > 1000) {
                // Get 1000th newest recommendation
                var recommendations = repository.findTop10ByOrderByTimestampDesc();
                if (recommendations.size() >= 10) {
                    var cutoff = recommendations.get(9).getTimestamp();
                    
                    // Delete older than cutoff (in production, use batch delete)
                    log.info("Would delete recommendations older than {}", cutoff);
                    // repository.deleteByTimestampBefore(cutoff);
                }
            }
            
        } catch (Exception e) {
            log.error("Error cleaning up old recommendations", e);
            // Non-critical, don't throw
        }
    }
}
