package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.TradeRecommendation;
import net.pautet.softs.demospring.entity.RecommendationHistory;
import net.pautet.softs.demospring.repository.RecommendationHistoryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Watches pending LIMIT BUY entries and performs post-fill actions:
 * - When a LIMIT BUY fills: marks executed and places OCO exit (TP/SL) if provided
 * - When time horizon expires without fill: cancels the LIMIT entry
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "trading.order-watcher.enabled", havingValue = "true", matchIfMissing = true)
public class OrderWatcherService {

    private final RecommendationHistoryRepository repository;
    private final BinanceApiService binanceApiService;
    private final BinanceTradingService tradingService;
    private final RecommendationPersistenceService persistenceService;

    public OrderWatcherService(RecommendationHistoryRepository repository,
                               BinanceApiService binanceApiService,
                               BinanceTradingService tradingService,
                               RecommendationPersistenceService persistenceService) {
        this.repository = repository;
        this.binanceApiService = binanceApiService;
        this.tradingService = tradingService;
        this.persistenceService = persistenceService;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 20000)
    public void watchPendingEntries() {
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(1);
            List<RecommendationHistory> pendingBuys = repository
                    .findBySignalAndEntryTypeAndExecutedFalseAndEntryOrderIdIsNotNullAndTimestampAfter(
                            TradeRecommendation.Signal.BUY,
                            TradeRecommendation.EntryType.LIMIT,
                            since
                    );
            List<RecommendationHistory> pendingSells = repository
                    .findBySignalAndEntryTypeAndExecutedFalseAndEntryOrderIdIsNotNullAndTimestampAfter(
                            TradeRecommendation.Signal.SELL,
                            TradeRecommendation.EntryType.LIMIT,
                            since
                    );

            log.debug("OrderWatcher: checking {} pending limit entries", pendingBuys.size());

            for (RecommendationHistory rec : pendingBuys) {
                try {
                    if (rec.getEntryOrderId() == null) continue;

                    var order = binanceApiService.getOrder(
                            BinanceTradingService.SYMBOL_ETHUSDC,
                            rec.getEntryOrderId(),
                            rec.getEntryClientOrderId()
                    );

                    // Update entry order meta
                    rec.setEntryOrderStatus(order.status());
                    rec.setEntryOrderType(order.type());
                    rec.setEntryClientOrderId(order.clientOrderId());

                    if (order.isFilled()) {
                        // Mark executed and persist execution result
                        rec.setExecuted(true);
                        Map<String, String> exec = new HashMap<>();
                        exec.put("orderId", String.valueOf(order.orderId()));
                        exec.put("executedQty", order.executedQty().toPlainString());
                        exec.put("avgPrice", order.getAveragePrice().toPlainString());
                        exec.put("status", order.status());
                        rec.setExecutionResult(exec);
                        repository.save(rec);

                        // Place OCO exit if TP/SL present
                        if (rec.getTakeProfit1() != null && rec.getStopLoss() != null) {
                            try {
                                var oco = tradingService.placeOcoSellExit(order.executedQty(), rec.getTakeProfit1(), rec.getStopLoss());
                                try {
                                    persistenceService.attachOcoDetailsByEntryOrderId(order.orderId(), oco);
                                } catch (Exception ignore) {}
                                log.info("Placed OCO exit for filled order {}: TP ${}, SL ${}",
                                        order.orderId(), rec.getTakeProfit1(), rec.getStopLoss());
                            } catch (Exception e) {
                                log.error("Failed to place OCO exit for order {}", order.orderId(), e);
                            }
                        }
                        continue;
                    }

                    // Pending: consider cancel on time horizon expiry
                    boolean pendingStatus = order.isPending();
                    if (pendingStatus && rec.getTimeHorizonMinutes() != null) {
                        LocalDateTime anchor = rec.getEntryPlacedAt() != null ? rec.getEntryPlacedAt() : rec.getTimestamp();
                        boolean expired = anchor
                                .plusMinutes(rec.getTimeHorizonMinutes())
                                .isBefore(LocalDateTime.now());
                        if (expired) {
                            try {
                                var canceled = binanceApiService.cancelOrder(
                                        BinanceTradingService.SYMBOL_ETHUSDC,
                                        rec.getEntryOrderId(),
                                        rec.getEntryClientOrderId()
                                );
                                rec.setEntryOrderStatus(canceled.status());
                                repository.save(rec);
                                log.info("Canceled stale LIMIT entry order {} after horizon {}m", rec.getEntryOrderId(), rec.getTimeHorizonMinutes());
                            } catch (Exception e) {
                                log.error("Failed to cancel stale order {}", rec.getEntryOrderId(), e);
                            }
                        }
                    }

                    // Save status updates
                    repository.save(rec);
                } catch (Exception ex) {
                    log.error("OrderWatcher error for rec id {}", rec.getId(), ex);
                }
            }

            // Handle pending LIMIT SELL orders (cancel on horizon expiry)
            for (RecommendationHistory rec : pendingSells) {
                try {
                    if (rec.getEntryOrderId() == null) continue;
                    var order = binanceApiService.getOrder(
                            BinanceTradingService.SYMBOL_ETHUSDC,
                            rec.getEntryOrderId(),
                            rec.getEntryClientOrderId()
                    );
                    rec.setEntryOrderStatus(order.status());
                    rec.setEntryOrderType(order.type());
                    rec.setEntryClientOrderId(order.clientOrderId());

                    boolean pendingStatus = order.isPending();
                    if (pendingStatus && rec.getTimeHorizonMinutes() != null) {
                        LocalDateTime anchor = rec.getEntryPlacedAt() != null ? rec.getEntryPlacedAt() : rec.getTimestamp();
                        boolean expired = anchor
                                .plusMinutes(rec.getTimeHorizonMinutes())
                                .isBefore(LocalDateTime.now());
                        if (expired) {
                            try {
                                var canceled = binanceApiService.cancelOrder(
                                        BinanceTradingService.SYMBOL_ETHUSDC,
                                        rec.getEntryOrderId(),
                                        rec.getEntryClientOrderId()
                                );
                                rec.setEntryOrderStatus(canceled.status());
                                repository.save(rec);
                                log.info("Canceled stale LIMIT SELL order {} after horizon {}m", rec.getEntryOrderId(), rec.getTimeHorizonMinutes());
                            } catch (Exception e) {
                                log.error("Failed to cancel stale SELL order {}", rec.getEntryOrderId(), e);
                            }
                        }
                    }

                    repository.save(rec);
                } catch (Exception ex) {
                    log.error("OrderWatcher SELL error for rec id {}", rec.getId(), ex);
                }
            }

            // Update OCO child status for recommendations with OCO list id
            List<RecommendationHistory> withOco = repository.findByOcoOrderListIdIsNotNullAndTimestampAfter(since);
            for (RecommendationHistory rec : withOco) {
                try {
                    var exit = rec.getExitOrders();
                    if (exit == null) continue;
                    boolean updated = false;
                    for (int i = 1; i <= 4; i++) {
                        String idKey = "order" + i + "Id";
                        String statusKey = "order" + i + "Status";
                        if (exit.containsKey(idKey)) {
                            try {
                                Long childId = Long.parseLong(exit.get(idKey));
                                var childOrder = binanceApiService.getOrder(BinanceTradingService.SYMBOL_ETHUSDC, childId, null);
                                if (childOrder != null && childOrder.status() != null) {
                                    exit.put(statusKey, childOrder.status());
                                    updated = true;
                                }
                            } catch (Exception ignore) {}
                        }
                    }
                    if (updated) {
                        rec.setExitOrders(exit);
                        repository.save(rec);
                    }
                } catch (Exception e) {
                    log.error("OrderWatcher OCO status update error for rec id {}", rec.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("OrderWatcher failed", e);
        }
    }
}
