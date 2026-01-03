package net.pautet.softs.demospring.exception;

/**
 * Exception thrown when Binance API operations fail.
 * This provides more specific error handling than generic RuntimeException.
 */
public class BinanceApiException extends RuntimeException {
    
    public BinanceApiException(String message) {
        super(message);
    }
    
    public BinanceApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
