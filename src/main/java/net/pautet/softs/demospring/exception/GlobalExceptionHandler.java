package net.pautet.softs.demospring.exception;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.NetatmoErrorResponse;
import net.pautet.softs.demospring.security.JwtExpiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(JwtExpiredException.class)
    public ResponseEntity<NetatmoErrorResponse> handleJwtExpiredException(JwtExpiredException ex) {
        log.warn("JWT token has expired: {}", ex.getMessage());
        NetatmoErrorResponse error = new NetatmoErrorResponse(new NetatmoErrorResponse.Error(401, "JWT token has expired"));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(NetatmoApiException.class)
    public ResponseEntity<NetatmoErrorResponse> handleNetatmoApiException(NetatmoApiException e) {
        log.warn("Netatmo exception received: {} {}", e.getError().error().code(), e.getError().error().message());
        return ResponseEntity.status(e.getStatus()).body(e.getError());
    }

    @ExceptionHandler(NetatmoRateLimitException.class)
    public ResponseEntity<String> handleRateLimitException(NetatmoRateLimitException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Netatmo API rate limit exceeded: " + e.getMessage());
    }

    @ExceptionHandler(NetatmoTimeoutException.class)
    public ResponseEntity<String> handleTimeoutException(NetatmoTimeoutException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body("Netatmo API request timed out: " + e.getMessage());
    }
}