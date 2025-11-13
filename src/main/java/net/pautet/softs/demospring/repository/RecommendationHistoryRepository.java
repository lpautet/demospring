package net.pautet.softs.demospring.repository;

import net.pautet.softs.demospring.dto.TradeRecommendation;
import net.pautet.softs.demospring.entity.RecommendationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Recommendation History
 * 
 * Provides access to historical trading recommendations for:
 * - AI memory/context retrieval
 * - Performance analysis
 * - Audit trail
 */
@Repository
public interface RecommendationHistoryRepository extends JpaRepository<RecommendationHistory, Long> {
    
    /**
     * Get most recent recommendation (for AI memory)
     */
    Optional<RecommendationHistory> findFirstByOrderByTimestampDesc();
    
    /**
     * Get recommendations from last N hours
     */
    @Query("SELECT r FROM RecommendationHistory r WHERE r.timestamp >= :since ORDER BY r.timestamp DESC")
    List<RecommendationHistory> findRecentSince(@Param("since") LocalDateTime since);
    
    /**
     * Get last N recommendations
     */
    List<RecommendationHistory> findTop10ByOrderByTimestampDesc();
    
    /**
     * Get executed recommendations (actual trades)
     */
    @Query("SELECT r FROM RecommendationHistory r WHERE r.executed = true ORDER BY r.timestamp DESC")
    List<RecommendationHistory> findExecutedRecommendations();
    
    /**
     * Get recommendations by signal type in time range
     */
    @Query("SELECT r FROM RecommendationHistory r WHERE r.signal = :signal AND r.timestamp >= :since ORDER BY r.timestamp DESC")
    List<RecommendationHistory> findBySignalSince(
        @Param("signal") TradeRecommendation.Signal signal,
        @Param("since") LocalDateTime since
    );
    
    /**
     * Count recommendations in last N hours
     */
    @Query("SELECT COUNT(r) FROM RecommendationHistory r WHERE r.timestamp >= :since")
    long countSince(@Param("since") LocalDateTime since);
    
    /**
     * Get signal changes (where signal differs from previous)
     * Useful for identifying position entries/exits
     */
    @Query("""
        SELECT r FROM RecommendationHistory r
        WHERE r.timestamp >= :since
        AND (r.signal != LAG(r.signal) OVER (ORDER BY r.timestamp)
             OR r.executed = true)
        ORDER BY r.timestamp DESC
        """)
    List<RecommendationHistory> findSignificantEventsSince(@Param("since") LocalDateTime since);

    /**
     * Find recent pending LIMIT BUY entries with an order id.
     * Useful for post-fill processing (e.g., placing OCO exits) and staleness checks.
     */
    List<RecommendationHistory> findBySignalAndEntryTypeAndExecutedFalseAndEntryOrderIdIsNotNullAndTimestampAfter(
            net.pautet.softs.demospring.dto.TradeRecommendation.Signal signal,
            net.pautet.softs.demospring.dto.TradeRecommendation.EntryType entryType,
            LocalDateTime since
    );

    /** Find the recommendation associated with a specific entry order id */
    java.util.Optional<RecommendationHistory> findFirstByEntryOrderId(Long entryOrderId);

    /** Find recommendations that already have an OCO order list to track exit order statuses */
    List<RecommendationHistory> findByOcoOrderListIdIsNotNullAndTimestampAfter(LocalDateTime since);
}
