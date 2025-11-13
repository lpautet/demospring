package net.pautet.softs.demospring.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.dto.AccountSummary;
import net.pautet.softs.demospring.dto.BinanceOrderResponse;
import net.pautet.softs.demospring.dto.BinanceTrade;
import net.pautet.softs.demospring.entity.RecommendationHistory;
import net.pautet.softs.demospring.repository.RecommendationHistoryRepository;
import net.pautet.softs.demospring.service.BinanceApiService;
import net.pautet.softs.demospring.service.BinanceTradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for ETH trading and market data
 * All trading operations use Binance Testnet
 */
@Slf4j
@RestController
@RequestMapping("/api/trading")
@AllArgsConstructor
public class TradingController {

    private final BinanceApiService binanceApiService;
    private final BinanceTradingService tradingService;
    private final RecommendationHistoryRepository recommendationRepository;

    /**
     * Get current ETH/USDC price
     * Returns: {"symbol":"ETHUSDC","price":"2450.50"}
     */
    @GetMapping("/eth/price")
    public net.pautet.softs.demospring.dto.BinanceTickerPrice getETHPrice() {
        log.debug("GET /api/trading/eth/price");
        return binanceApiService.getPrice(BinanceApiService.ETHUSDC);
    }

    /**
     * Get open orders (default symbol: ETHUSDC)
     */
    @GetMapping("/open-orders")
    public ResponseEntity<List<net.pautet.softs.demospring.dto.BinanceOrderResponse>> getOpenOrders(
            @RequestParam(value = "symbol", defaultValue = "ETHUSDC") String symbol) {
        log.debug("GET /api/trading/open-orders - symbol: {}", symbol);
        var orders = binanceApiService.getOpenOrders(symbol);
        return ResponseEntity.ok(orders);
    }

    /**
     * Get fees (maker/taker) for a symbol
     */
    @GetMapping("/fees")
    public ResponseEntity<Map<String, Object>> getFees(
            @RequestParam(value = "symbol", defaultValue = "ETHUSDC") String symbol) {
        log.debug("GET /api/trading/fees - symbol: {}", symbol);
        var fee = binanceApiService.getTradeFees(symbol);
        Map<String, Object> payload = Map.of(
                "symbol", fee.symbol(),
                "maker", fee.makerCommission(),
                "taker", fee.takerCommission()
        );
        return ResponseEntity.ok(payload);
    }

    /**
     * Cancel an order (by orderId or clientOrderId)
     */
    @PostMapping("/cancel-order")
    public ResponseEntity<BinanceOrderResponse> cancelOrder(@RequestBody CancelOrderRequest req) {
        if ((req.getOrderId() == null || req.getOrderId() <= 0) && (req.getClientOrderId() == null || req.getClientOrderId().isBlank())) {
            return ResponseEntity.badRequest().build();
        }
        String symbol = (req.getSymbol() == null || req.getSymbol().isBlank()) ? BinanceTradingService.SYMBOL_ETHUSDC : req.getSymbol();
        log.info("POST /api/trading/cancel-order - symbol: {} orderId: {} clientId: {}", symbol, req.getOrderId(), req.getClientOrderId());
        var canceled = binanceApiService.cancelOrder(symbol, req.getOrderId(), req.getClientOrderId());
        return ResponseEntity.ok(canceled);
    }

    /**
     * Get order details (by orderId or clientOrderId)
     * Useful for classifying realized OCO leg (TP vs SL) in the UI.
     */
    @GetMapping("/order")
    public ResponseEntity<BinanceOrderResponse> getOrder(
            @RequestParam(value = "symbol", defaultValue = "ETHUSDC") String symbol,
            @RequestParam(value = "orderId", required = false) Long orderId,
            @RequestParam(value = "clientOrderId", required = false) String clientOrderId) {
        if ((orderId == null || orderId <= 0) && (clientOrderId == null || clientOrderId.isBlank())) {
            return ResponseEntity.badRequest().build();
        }
        log.debug("GET /api/trading/order - symbol: {} orderId: {} clientId: {}", symbol, orderId, clientOrderId);
        var order = binanceApiService.getOrder(symbol, orderId, clientOrderId);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }

    /**
     * Refresh and return OCO status for a recommendation (updates exitOrders with child order status/type)
     */
    @GetMapping("/oco-status")
    public ResponseEntity<Map<String, Object>> getOcoStatus(@RequestParam("recId") Long recId) {
        var opt = recommendationRepository.findById(recId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var rec = opt.get();
        var exit = rec.getExitOrders();
        if (exit != null) {
            boolean updated = false;
            for (int i = 1; i <= 4; i++) {
                String idKey = "order" + i + "Id";
                if (exit.containsKey(idKey)) {
                    try {
                        Long childId = Long.parseLong(exit.get(idKey));
                        var child = binanceApiService.getOrder(BinanceTradingService.SYMBOL_ETHUSDC, childId, null);
                        if (child != null) {
                            exit.put("order" + i + "Status", child.status());
                            exit.put("order" + i + "Type", child.type());
                            updated = true;
                        }
                    } catch (Exception ignore) {}
                }
            }
            if (updated) {
                rec.setExitOrders(exit);
                recommendationRepository.save(rec);
            }
        }
        Map<String, Object> payload = Map.of(
                "orderListId", rec.getOcoOrderListId(),
                "exitOrders", rec.getExitOrders()
        );
        return ResponseEntity.ok(payload);
    }

    /**
     * Get 24hr ticker statistics for ETH/USDC
     * Returns comprehensive market data including:
     * - priceChange, priceChangePercent
     * - weightedAvgPrice
     * - highPrice, lowPrice
     * - volume, quoteVolume
     */
    @GetMapping("/eth/ticker24h")
    public net.pautet.softs.demospring.dto.Binance24hrTicker getETH24hrTicker() {
        log.debug("GET /api/trading/eth/ticker24h");
        return binanceApiService.get24hrTicker(BinanceApiService.ETHUSDC);
    }

    /**
     * Get candlestick/kline data for any trading pair
     * @param symbol Trading pair (default: ETHUSDC)
     * @param interval Kline interval: 1m, 5m, 15m, 1h, 4h, 1d, etc. (default: 1h)
     * @param limit Number of klines to return (max 1000, default: 100)
     * 
     * Returns array of arrays (Binance format) for frontend compatibility:
     * [[openTime, open, high, low, close, volume, closeTime, ...], ...]
     */
    @GetMapping("/eth/klines")
    public java.util.List<Object[]> getETHKlines(
            @RequestParam(defaultValue = "ETHUSDC") String symbol,
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam(defaultValue = "100") int limit) {
        log.debug("GET /api/trading/eth/klines - symbol: {}, interval: {}, limit: {}", symbol, interval, limit);
        
        // Validate interval
        if (!isValidInterval(interval)) {
            throw new IllegalArgumentException("Invalid interval. Valid: 1m, 5m, 15m, 1h, 4h, 1d");
        }
        
        // Validate limit
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Invalid limit. Must be between 1 and 1000");
        }
        
        // Get typed klines and convert to array format for frontend
        var klines = binanceApiService.getKlines(symbol, interval, limit);
        return klines.stream()
                .map(k -> new Object[] {
                    k.openTime(),
                    k.open(),
                    k.high(),
                    k.low(),
                    k.close(),
                    k.volume(),
                    k.closeTime(),
                    k.quoteVolume(),
                    k.trades(),
                    k.takerBuyBaseVolume(),
                    k.takerBuyQuoteVolume(),
                    k.ignore()
                })
                .toList();
    }

    private boolean isValidInterval(String interval) {
        return interval.matches("^(1|3|5|15|30)m$|^(1|2|4|6|8|12)h$|^(1|3)d$|^1w$|^1M$");
    }

    // ==================== Trading Endpoints (Binance Testnet) ====================

    /**
     * Get account summary from Binance Testnet
     * Returns balance, value, and stats
     */
    @GetMapping("/portfolio")
    public ResponseEntity<AccountSummary> getPortfolio(Authentication authentication) {
        String username = authentication.getName();
        log.debug("GET /api/trading/portfolio - user: {}", username);
        
        AccountSummary summary = tradingService.getAccountSummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * Execute BUY trade on Binance Testnet
     */
    @PostMapping("/buy")
    public ResponseEntity<?> executeBuy(@RequestBody TradeRequest request, Authentication authentication) {
        String username = authentication.getName();
        log.info("POST /api/trading/buy - user: {} amount: ${}", username, request.getAmount());
        
        try {
            BinanceOrderResponse result = tradingService.buyETH(request.getAmount());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("BUY failed for {}: {}", username, e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("BUY error for {}", username, e);
            return ResponseEntity.internalServerError().body(new ErrorResponse("Trade execution failed: " + e.getMessage()));
        }
    }

    /**
     * Execute SELL trade on Binance Testnet
     */
    @PostMapping("/sell")
    public ResponseEntity<?> executeSell(@RequestBody TradeRequest request, Authentication authentication) {
        String username = authentication.getName();
        log.info("POST /api/trading/sell - user: {} amount: {} ETH", username, request.getAmount());
        
        try {
            BinanceOrderResponse result = tradingService.sellETH(request.getAmount());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("SELL failed for {}: {}", username, e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("SELL error for {}", username, e);
            return ResponseEntity.internalServerError().body(new ErrorResponse("Trade execution failed: " + e.getMessage()));
        }
    }

    /**
     * Get trade history from Binance Testnet
     */
    @GetMapping("/trades")
    public ResponseEntity<List<BinanceTrade>> getTradeHistory(Authentication authentication) {
        String username = authentication.getName();
        log.debug("GET /api/trading/trades - user: {}", username);
        
        List<BinanceTrade> trades = tradingService.getRecentTrades(50);
        return ResponseEntity.ok(trades);
    }

    /**
     * Check trading mode (always testnet)
     */
    @GetMapping("/mode")
    public ResponseEntity<Map<String, Object>> getTradingMode() {
        Map<String, Object> mode = Map.of(
            "mode", "TESTNET",
            "description", "Binance Testnet - Real execution, fake money",
            "testnet", true,
            "configured", tradingService.isConfigured()
        );
        return ResponseEntity.ok(mode);
    }

    // ==================== AI Recommendation History ====================

    /**
     * Get recommendation history with optional filters
     * @param limit Number of recommendations to return (default: 20, max: 100)
     * @param executed Filter by execution status (optional)
     * @param signal Filter by signal type: BUY, SELL, HOLD (optional)
     */
    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationHistory>> getRecommendationHistory(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Boolean executed,
            @RequestParam(required = false) String signal) {
        log.debug("GET /api/trading/recommendations - limit: {}, executed: {}, signal: {}", 
            limit, executed, signal);
        
        // Validate limit
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid limit. Must be between 1 and 100");
        }
        
        List<RecommendationHistory> recommendations;
        
        if (executed != null && executed) {
            // Get only executed recommendations
            recommendations = recommendationRepository.findExecutedRecommendations()
                .stream()
                .limit(limit)
                .toList();
        } else if (signal != null) {
            // Filter by signal type
            try {
                var signalEnum = net.pautet.softs.demospring.dto.TradeRecommendation.Signal.valueOf(signal.toUpperCase());
                recommendations = recommendationRepository.findBySignalSince(
                    signalEnum, 
                    java.time.LocalDateTime.now().minusDays(30)
                ).stream()
                .limit(limit)
                .toList();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid signal. Valid values: BUY, SELL, HOLD");
            }
        } else {
            // Get recent recommendations
            recommendations = recommendationRepository.findRecentSince(
                java.time.LocalDateTime.now().minusDays(30)
            ).stream()
            .limit(limit)
            .toList();
        }
        
        return ResponseEntity.ok(recommendations);
    }

    /**
     * Get recommendation statistics
     */
    @GetMapping("/recommendations/stats")
    public ResponseEntity<Map<String, Object>> getRecommendationStats() {
        log.debug("GET /api/trading/recommendations/stats");
        
        var cutoff = java.time.LocalDateTime.now().minusDays(30);
        var all = recommendationRepository.findRecentSince(cutoff);
        
        long total = all.size();
        long executed = all.stream().filter(RecommendationHistory::getExecuted).count();
        long buyCount = all.stream().filter(r -> r.getSignal() == net.pautet.softs.demospring.dto.TradeRecommendation.Signal.BUY).count();
        long sellCount = all.stream().filter(r -> r.getSignal() == net.pautet.softs.demospring.dto.TradeRecommendation.Signal.SELL).count();
        long holdCount = all.stream().filter(r -> r.getSignal() == net.pautet.softs.demospring.dto.TradeRecommendation.Signal.HOLD).count();
        
        Map<String, Object> stats = Map.of(
            "total", total,
            "executed", executed,
            "executionRate", total > 0 ? String.format("%.1f%%", (executed * 100.0 / total)) : "0%",
            "signals", Map.of(
                "BUY", buyCount,
                "SELL", sellCount,
                "HOLD", holdCount
            ),
            "period", "Last 30 days"
        );
        
        return ResponseEntity.ok(stats);
    }

    // ==================== DTOs ====================

    @Data
    public static class TradeRequest {
        private BigDecimal amount; // USD for BUY, ETH for SELL
        private String reason; // Optional reasoning
    }

    @Data
    public static class CancelOrderRequest {
        private String symbol;
        private Long orderId;
        private String clientOrderId;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
    }
}
