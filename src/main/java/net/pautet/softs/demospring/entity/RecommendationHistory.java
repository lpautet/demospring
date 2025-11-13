package net.pautet.softs.demospring.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import net.pautet.softs.demospring.dto.TradeRecommendation;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Recommendation History Entity
 * 
 * Stores all AI trading recommendations for:
 * - Audit trail and compliance
 * - Performance analysis
 * - AI memory/context for future recommendations
 * - Pattern detection and learning
 */
@Entity
@Table(name = "recommendation_history", indexes = {
    @Index(name = "idx_timestamp", columnList = "timestamp DESC"),
    @Index(name = "idx_executed", columnList = "executed"),
    @Index(name = "idx_signal", columnList = "signal")
})
@Data
public class RecommendationHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeRecommendation.Signal signal;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeRecommendation.Confidence confidence;
    
    @Column(precision = 20, scale = 8)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TradeRecommendation.AmountType amountType;
    
    @Column(columnDefinition = "TEXT")
    private String reasoning;

    /**
     * Optional execution details provided by AI for better trade planning
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TradeRecommendation.EntryType entryType;

    @Column(precision = 20, scale = 8)
    private BigDecimal entryPrice;

    // Entry order tracking (for LIMIT entries)
    @Column
    private Long entryOrderId;

    @Column(length = 20)
    private String entryOrderStatus; // NEW, PARTIALLY_FILLED, FILLED, CANCELED, EXPIRED

    @Column(length = 20)
    private String entryOrderType; // MARKET, LIMIT, etc.

    @Column(length = 64)
    private String entryClientOrderId;

    /**
     * Actual exchange placement time for the entry order
     * Used for accurate horizon expiry instead of recommendation timestamp
     */
    @Column
    private LocalDateTime entryPlacedAt;

    @Column(precision = 20, scale = 8)
    private BigDecimal stopLoss;

    @Column(precision = 20, scale = 8)
    private BigDecimal takeProfit1;

    @Column(precision = 20, scale = 8)
    private BigDecimal takeProfit2;

    @Column
    private Integer timeHorizonMinutes;

    /**
     * Exit orders metadata (e.g., OCO order list id and child order ids)
     */
    @Column
    private Long ocoOrderListId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private java.util.Map<String, String> exitOrders;
    
    /**
     * AI's working memory - stored as JSON
     * Contains bullet points the AI wants to remember for next analysis
     * Uses JSONB in PostgreSQL, TEXT in H2
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> aiMemory;
    
    /**
     * Was this recommendation actually executed?
     */
    @Column(nullable = false)
    private Boolean executed = false;
    
    /**
     * Execution details if trade was executed
     * Stored as JSON containing: orderId, executedQty, avgPrice, status
     * Uses JSONB in PostgreSQL, TEXT in H2
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private java.util.Map<String, String> executionResult;
    
    /**
     * Market context at time of recommendation (for analysis)
     * Snapshot of key market conditions when recommendation was made
     * Uses JSONB in PostgreSQL, TEXT in H2
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private java.util.Map<String, String> marketContext;
    
    /**
     * Create from TradeRecommendation DTO
     */
    public static RecommendationHistory fromRecommendation(TradeRecommendation rec) {
        RecommendationHistory history = new RecommendationHistory();
        history.setTimestamp(LocalDateTime.now());
        history.setSignal(rec.signal());
        history.setConfidence(rec.confidence());
        history.setAmount(rec.amount());
        history.setAmountType(rec.amountType());
        history.setReasoning(rec.reasoning());
        history.setEntryType(rec.entryType());
        history.setEntryPrice(rec.entryPrice());
        history.setStopLoss(rec.stopLoss());
        history.setTakeProfit1(rec.takeProfit1());
        history.setTakeProfit2(rec.takeProfit2());
        history.setTimeHorizonMinutes(rec.timeHorizonMinutes());
        history.setAiMemory(rec.memory());
        history.setExecuted(false);
        return history;
    }
}
