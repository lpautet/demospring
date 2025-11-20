package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.config.BinanceConfig;
import net.pautet.softs.demospring.dto.*;
import net.pautet.softs.demospring.exception.BinanceApiException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.pautet.softs.demospring.service.BinanceTradingService.SYMBOL_ETHUSDC;

/**
 * Service for making Binance API calls.
 * Supports both testnet and production environments.
 */
@Slf4j
@Service
public class BinanceApiService {

    private static final Duration CONNECT_TIMEOUT_DURATION = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT_DURATION = Duration.ofSeconds(10);
    private static final String BINANCE_API_URI = "https://api.binance.com";
    private static final String BINANCE_TESTNET_URI = "https://testnet.binance.vision";
    private static final String PARAM_SYMBOL = "symbol";
    private static final String PARAM_TIMESTAMP = "timestamp";
    private static final String PARAM_RECV_WINDOW = "recvWindow";
    private static final String PARAM_SIGNATURE = "signature";
    private static final String HEADER_API_KEY = "X-MBX-APIKEY";
    public static final String ETHUSDC = "ETHUSDC";

    private final BinanceConfig binanceConfig;

    public BinanceApiService(BinanceConfig binanceConfig) {
        this.binanceConfig = binanceConfig;
    }

    /**
     * Get trade fees (maker/taker) for a symbol using /sapi/v1/asset/tradeFee (USER_DATA).
     * Falls back to /api/v3/account maker/taker commission if /sapi endpoint is unavailable (e.g., testnet limitations).
     */
    public BinanceTradeFee getTradeFees(String symbol) {
        final String sym = (symbol == null || symbol.isEmpty()) ? ETHUSDC : symbol;
        long now = System.currentTimeMillis();
        var cached = tradeFeeCache.get(sym);
        if (cached != null && (now - tradeFeeCacheTs) < TRADE_FEE_TTL_MS) {
            return cached;
        }

        if (isMissingApiCredentials()) {
            throw new IllegalStateException("Binance API credentials not configured");
        }

        long timestamp = System.currentTimeMillis();
        StringBuilder query = new StringBuilder();
        query.append(PARAM_SYMBOL).append("=").append(sym)
             .append("&").append(PARAM_RECV_WINDOW).append("=").append(5000)
             .append("&").append(PARAM_TIMESTAMP).append("=").append(timestamp);
        String signature = generateSignature(query.toString());

        RestClient client = createBinanceApiClient();
        String uri = "/sapi/v1/asset/tradeFee?" + query + "&" + PARAM_SIGNATURE + "=" + signature;

        try {
            var list = client.get()
                    .uri(uri)
                    .header(HEADER_API_KEY, binanceConfig.getApiKey())
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<java.util.List<net.pautet.softs.demospring.dto.BinanceTradeFee>>() {});
            BinanceTradeFee fee = null;
            if (list != null && !list.isEmpty()) {
                fee = list.stream()
                        .filter(f -> sym.equalsIgnoreCase(f.symbol()))
                        .findFirst()
                        .orElse(list.getFirst());
            }
            if (fee != null) {
                tradeFeeCache.put(sym, fee);
                tradeFeeCacheTs = now;
                return fee;
            }
        } catch (Exception e) {
            log.warn("TradeFee endpoint unavailable, falling back to account commissions: {}", e.getMessage());
        }

        // Fallback to /api/v3/account maker/taker commissions (basis points -> percent)
        var acc = getAccountInfo();
        BigDecimal maker = BigDecimal.valueOf(acc.getMakerCommissionPercent());
        BigDecimal taker = BigDecimal.valueOf(acc.getTakerCommissionPercent());
        var fee = new BinanceTradeFee(sym, maker, taker);
        tradeFeeCache.put(sym, fee);
        tradeFeeCacheTs = now;
        return fee;
    }

    // ==== Simple in-memory cache for trade fees (per symbol) ====
    private final Map<String, BinanceTradeFee> tradeFeeCache = new ConcurrentHashMap<>();
    private volatile long tradeFeeCacheTs = 0L;
    private static final long TRADE_FEE_TTL_MS = 10 * 60 * 1000; // 10 minutes

    /**
     * Get open orders. If symbol is provided, filters to that symbol.
     */
    public List<BinanceOrderResponse> getOpenOrders(String symbol) {
        if (isMissingApiCredentials()) {
            throw new IllegalStateException("Binance API credentials not configured");
        }

        long timestamp = System.currentTimeMillis();

        StringBuilder query = new StringBuilder();
        if (symbol != null && !symbol.isEmpty()) {
            query.append(PARAM_SYMBOL).append("=").append(symbol).append("&");
        }
        query.append(PARAM_TIMESTAMP).append("=").append(timestamp);
        String signature = generateSignature(query.toString());

        RestClient client = createBinanceApiClient();
        return client.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/v3/openOrders");
                    if (symbol != null && !symbol.isEmpty()) {
                        builder.queryParam(PARAM_SYMBOL, symbol);
                    }
                    builder.queryParam(PARAM_TIMESTAMP, timestamp);
                    return builder
                            .queryParam(PARAM_SIGNATURE, signature)
                            .build();
                })
                .header(HEADER_API_KEY, binanceConfig.getApiKey())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    /**
     * Get a specific order by orderId or origClientOrderId
     */
    public BinanceOrderResponse getOrder(String symbol, Long orderId, String origClientOrderId) {
        if (isMissingApiCredentials()) {
            throw new IllegalStateException("Binance API credentials not configured");
        }

        long timestamp = System.currentTimeMillis();

        StringBuilder query = new StringBuilder();
        query.append(PARAM_SYMBOL).append("=").append(symbol)
             .append("&").append(PARAM_TIMESTAMP).append("=").append(timestamp);
        if (orderId != null) {
            query.append("&orderId=").append(orderId);
        } else if (origClientOrderId != null && !origClientOrderId.isEmpty()) {
            query.append("&origClientOrderId=").append(origClientOrderId);
        } else {
            throw new IllegalArgumentException("Either orderId or origClientOrderId must be provided");
        }

        String signature = generateSignature(query.toString());

        RestClient client = createBinanceApiClient();
        String uri = "/api/v3/order?" + query + "&" + PARAM_SIGNATURE + "=" + signature;
        return client.get()
                .uri(uri)
                .header(HEADER_API_KEY, binanceConfig.getApiKey())
                .retrieve()
                .body(BinanceOrderResponse.class);
    }

    /**
     * Cancel an order by orderId or origClientOrderId
     */
    public BinanceOrderResponse cancelOrder(String symbol, Long orderId, String origClientOrderId) {
        if (isMissingApiCredentials()) {
            throw new IllegalStateException("Binance API credentials not configured");
        }

        long timestamp = System.currentTimeMillis();

        StringBuilder query = new StringBuilder();
        query.append(PARAM_SYMBOL).append("=").append(symbol)
             .append("&").append(PARAM_TIMESTAMP).append("=").append(timestamp);
        if (orderId != null) {
            query.append("&orderId=").append(orderId);
        } else if (origClientOrderId != null && !origClientOrderId.isEmpty()) {
            query.append("&origClientOrderId=").append(origClientOrderId);
        } else {
            throw new IllegalArgumentException("Either orderId or origClientOrderId must be provided");
        }

        String signature = generateSignature(query.toString());

        RestClient client = createBinanceApiClient();
        String uri = "/api/v3/order?" + query + "&" + PARAM_SIGNATURE + "=" + signature;
        return client.delete()
                .uri(uri)
                .header(HEADER_API_KEY, binanceConfig.getApiKey())
                .retrieve()
                .body(BinanceOrderResponse.class);
    }

    /**
     * Get current price ticker for any symbol
     * @param symbol Trading pair (e.g., "ETHUSDC", "BTCUSDT")
     * Note: Caching disabled due to deserialization issues with complex records
     */
    public BinanceTickerPrice getPrice(String symbol) {
        log.debug("Fetching {} price (cache miss)", symbol);
        RestClient client = createBinanceApiClient();
        return client.get()
                .uri("/api/v3/ticker/price?symbol=" + symbol)
                .retrieve()
                .body(BinanceTickerPrice.class);
    }

    /**
     * Get 24hr ticker statistics for any symbol
     * Includes price change, high, low, volume
     * @param symbol Trading pair (e.g., "ETHUSDC", "BTCUSDT")
     * Note: Caching disabled due to deserialization issues with complex records
     */
    public Binance24hrTicker get24hrTicker(String symbol) {
        log.debug("Fetching {} 24hr ticker (cache miss)", symbol);
        RestClient client = createBinanceApiClient();
        return client.get()
                .uri("/api/v3/ticker/24hr?symbol=" + symbol)
                .retrieve()
                .body(Binance24hrTicker.class);
    }

    /**
     * Get candlestick data (klines) for any symbol
     * @param symbol Trading pair (e.g., "ETHUSDC", "BTCUSDT")
     * @param interval 1m, 5m, 15m, 1h, 4h, 1d, etc.
     * @param limit Number of candles (max 1000)
     * @return List of klines/candlesticks
     */
    public List<BinanceKline> getKlines(String symbol, String interval, int limit) {
        return getKlines(symbol, interval, limit, null, null);
    }

    /**
     * Get candlestick data (klines) with optional time bounds
     * @param startTime inclusive start time in ms (optional)
     * @param endTime inclusive end time in ms (optional)
     */
    public List<BinanceKline> getKlines(String symbol, String interval, int limit, Long startTime, Long endTime) {
        log.debug("Fetching {} klines - interval: {} limit: {} startTime: {} endTime: {} (cache miss)", symbol, interval, limit, startTime, endTime);
        RestClient client = createBinanceApiClient();
        return client.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/api/v3/klines")
                            .queryParam(PARAM_SYMBOL, symbol)
                            .queryParam("interval", interval)
                            .queryParam("limit", limit);
                    if (startTime != null) builder.queryParam("startTime", startTime);
                    if (endTime != null) builder.queryParam("endTime", endTime);
                    return builder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<List<BinanceKline>>() {});
    }

    /**
     * Get exchange information including trading rules and filters
     * Can optionally filter by symbol
     * @param symbol Optional - specific symbol to get info for (e.g., "ETHUSDC")
     * @return Exchange info with all symbols and their trading rules
     */
    public BinanceExchangeInfo getExchangeInfo(String symbol) {
        log.debug("Fetching exchange info for symbol: {}", symbol != null ? symbol : "ALL");
        RestClient client = createBinanceApiClient();
        
        return client.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/v3/exchangeInfo");
                    if (symbol != null && !symbol.isEmpty()) {
                        builder.queryParam(PARAM_SYMBOL, symbol);
                    }
                    return builder.build();
                })
                .retrieve()
                .body(BinanceExchangeInfo.class);
    }

    /**
     * Get account information including balances
     * Requires API key and secret
     * @return Account info with balances, permissions, and trading status
     */
    public BinanceAccountInfo getAccountInfo() {
        if (isMissingApiCredentials()) {
            throw new IllegalStateException("Binance API credentials not configured");
        }

        long timestamp = System.currentTimeMillis();
        String queryString = String.format("timestamp=%d", timestamp);
        String signature = generateSignature(queryString);

        RestClient client = createBinanceApiClient();
        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/account")
                        .queryParam(PARAM_TIMESTAMP, timestamp)
                        .queryParam(PARAM_SIGNATURE, signature)
                        .build())
                .header(HEADER_API_KEY, binanceConfig.getApiKey())
                .retrieve()
                .body(BinanceAccountInfo.class);
    }

    /**
     * Place a market buy order
     * @param symbol e.g., "ETHUSDC"
     * @param quoteOrderQty Amount in quote currency (USDC)
     * @return Order response with execution details
     */
    public BinanceOrderResponse placeMarketBuyOrder(String symbol, String quoteOrderQty) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAM_SYMBOL, symbol);
        params.put("side", "BUY");
        params.put("type", "MARKET");
        params.put("quoteOrderQty", quoteOrderQty);
        
        return placeOrder(params);
    }

    /**
     * Place a market sell order
     * @param symbol e.g., "ETHUSDC"
     * @param quantity Amount in base currency (ETH)
     * @return Order response with execution details
     */
    public BinanceOrderResponse placeMarketSellOrder(String symbol, String quantity) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAM_SYMBOL, symbol);
        params.put("side", "SELL");
        params.put("type", "MARKET");
        params.put("quantity", quantity);
        
        return placeOrder(params);
    }
    
    /**
     * Place a LIMIT order (BUY or SELL)
     * Quantity is in base asset units (e.g., ETH). Price is in quote (e.g., USDC).
     * timeInForce typically GTC/IOC/FOK. Use GTC by default.
     */
    public BinanceOrderResponse placeLimitOrder(String symbol, String side, String quantity, String price, String timeInForce) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAM_SYMBOL, symbol);
        params.put("side", side);
        params.put("type", "LIMIT");
        params.put("timeInForce", timeInForce != null ? timeInForce : "GTC");
        params.put("quantity", quantity);
        params.put("price", price);
        return placeOrder(params);
    }
    
    /**
     * Private helper to place orders (DRY principle)
     * Handles authentication, signature, and returns typed response
     */
    private BinanceOrderResponse placeOrder(Map<String, String> params) {
        if (isMissingApiCredentials()) {
            throw new IllegalStateException("Binance API credentials not configured");
        }

        long timestamp = System.currentTimeMillis();
        
        // Build query string for signature
        StringBuilder queryStringBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!queryStringBuilder.isEmpty()) {
                queryStringBuilder.append("&");
            }
            queryStringBuilder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        queryStringBuilder.append("&timestamp=").append(timestamp);
        
        String signature = generateSignature(queryStringBuilder.toString());

        RestClient client = createBinanceApiClient();
        return client.post()
                .uri(uriBuilder -> {
                    UriBuilder builder = uriBuilder.path("/api/v3/order");
                    for (Map.Entry<String, String> entry : params.entrySet()) {
                        builder.queryParam(entry.getKey(), entry.getValue());
                    }
                    return builder
                            .queryParam(PARAM_TIMESTAMP, timestamp)
                            .queryParam(PARAM_SIGNATURE, signature)
                            .build();
                })
                .header(HEADER_API_KEY, binanceConfig.getApiKey())
                .retrieve()
                .body(BinanceOrderResponse.class);
    }

    /**
     * Place an OCO sell order (limit sell + stop-limit sell) for exits
     * Endpoint: POST /api/v3/order/oco
     */
    public net.pautet.softs.demospring.dto.BinanceOcoOrderResponse placeOcoSellOrder(
            String symbol,
            String quantity,
            String price,
            String stopPrice,
            String stopLimitPrice,
            String stopLimitTimeInForce
    ) {
        if (isMissingApiCredentials()) {
            throw new IllegalStateException("Binance API credentials not configured");
        }

        long timestamp = System.currentTimeMillis();

        // Build query string for signature
        StringBuilder query = new StringBuilder();
        query.append(PARAM_SYMBOL).append("=").append(symbol)
             .append("&side=SELL")
             .append("&quantity=").append(quantity)
             .append("&price=").append(price)
             .append("&stopPrice=").append(stopPrice)
             .append("&stopLimitPrice=").append(stopLimitPrice)
             .append("&stopLimitTimeInForce=").append(stopLimitTimeInForce != null ? stopLimitTimeInForce : "GTC")
             .append("&timestamp=").append(timestamp);

        String signature = generateSignature(query.toString());

        RestClient client = createBinanceApiClient();
        return client.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/order/oco")
                        .queryParam(PARAM_SYMBOL, symbol)
                        .queryParam("side", "SELL")
                        .queryParam("quantity", quantity)
                        .queryParam("price", price)
                        .queryParam("stopPrice", stopPrice)
                        .queryParam("stopLimitPrice", stopLimitPrice)
                        .queryParam("stopLimitTimeInForce", stopLimitTimeInForce != null ? stopLimitTimeInForce : "GTC")
                        .queryParam(PARAM_TIMESTAMP, timestamp)
                        .queryParam(PARAM_SIGNATURE, signature)
                        .build())
                .header(HEADER_API_KEY, binanceConfig.getApiKey())
                .retrieve()
                .body(net.pautet.softs.demospring.dto.BinanceOcoOrderResponse.class);
    }

    /**
     * Get all trades for a symbol
     * @param symbol e.g., "ETHUSDC"
     * @param limit Number of trades to retrieve (max 1000)
     * @return List of trades with execution details
     */
    public List<BinanceTrade> getMyTrades(String symbol, int limit) {
        if (isMissingApiCredentials()) {
            throw new IllegalStateException("Binance API credentials not configured");
        }

        long timestamp = System.currentTimeMillis();
        String queryString = String.format(PARAM_SYMBOL + "=%s&limit=%d&timestamp=%d",
                symbol, limit, timestamp);
        String signature = generateSignature(queryString);

        RestClient client = createBinanceApiClient();
        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/myTrades")
                        .queryParam(PARAM_SYMBOL, symbol)
                        .queryParam("limit", limit)
                        .queryParam(PARAM_TIMESTAMP, timestamp)
                        .queryParam(PARAM_SIGNATURE, signature)
                        .build())
                .header(HEADER_API_KEY, binanceConfig.getApiKey())
                .retrieve()
                .body(new ParameterizedTypeReference<List<BinanceTrade>>() {});
    }

    /**
     * Generate HMAC SHA256 signature for authenticated requests
     */
    private String generateSignature(String data) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    binanceConfig.getApiSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new BinanceApiException("HmacSHA256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new BinanceApiException("Invalid API secret key for signature generation", e);
        }
    }

    /**
     * Check if API credentials are missing or not configured
     */
    private boolean isMissingApiCredentials() {
        return binanceConfig.getApiKey() == null || binanceConfig.getApiKey().isEmpty()
                || binanceConfig.getApiSecret() == null || binanceConfig.getApiSecret().isEmpty();
    }

    private RestClient createBinanceApiClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT_DURATION.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT_DURATION.toMillis());
        
        String baseUrl = binanceConfig.isTestnet() ? BINANCE_TESTNET_URI : BINANCE_API_URI;
        log.debug("Using Binance API: {} (testnet: {})", baseUrl, binanceConfig.isTestnet());
        
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }



    // Add this method
    public double getSessionVwap() {
        try {
            ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
            ZonedDateTime startOfDay = now.toLocalDate().atStartOfDay(ZoneOffset.UTC);

            List<BinanceKline> klines = getKlines(
                    SYMBOL_ETHUSDC,
                    "5m",
                    (int) ((now.toEpochSecond() - startOfDay.toEpochSecond()) / 300)  // number of 5m candles
            );

            double totalVolume = 0.0;
            double volumePriceSum = 0.0;

            for (BinanceKline k : klines) {
                double price = (k.highAsDouble() + k.lowAsDouble() + k.closeAsDouble()) / 3.0; // typical price
                double volume = k.volumeAsDouble();
                volumePriceSum += price * volume;
                totalVolume += volume;
            }

            return totalVolume > 0 ? volumePriceSum / totalVolume : get24hrTicker(SYMBOL_ETHUSDC).lastPriceAsDouble();

        } catch (Exception e) {
            log.warn("Failed to calculate session VWAP, using last price", e);
            return get24hrTicker(SYMBOL_ETHUSDC).lastPriceAsDouble();
        }
    }
}
