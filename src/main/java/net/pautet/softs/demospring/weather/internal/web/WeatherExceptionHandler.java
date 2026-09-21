package net.pautet.softs.demospring.weather.internal.web;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.weather.NetatmoApiException;
import net.pautet.softs.demospring.weather.internal.NetatmoRateLimitException;
import net.pautet.softs.demospring.weather.internal.NetatmoTimeoutException;
import net.pautet.softs.demospring.weather.internal.NetatmoUnthorizedException;
import net.pautet.softs.demospring.weather.internal.model.NetatmoErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
class WeatherExceptionHandler {

    @ExceptionHandler(NetatmoApiException.class)
    ResponseEntity<NetatmoErrorResponse> handleNetatmoApiException(NetatmoApiException exception) {
        log.warn("Netatmo exception received: {} {}", exception.getError().error().code(),
                exception.getError().error().message());
        return ResponseEntity.status(exception.getStatus()).body(exception.getError());
    }

    @ExceptionHandler(NetatmoRateLimitException.class)
    ResponseEntity<String> handleRateLimitException(NetatmoRateLimitException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Netatmo API rate limit exceeded: " + exception.getMessage());
    }

    @ExceptionHandler(NetatmoTimeoutException.class)
    ResponseEntity<String> handleTimeoutException(NetatmoTimeoutException exception) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body("Netatmo API request timed out: " + exception.getMessage());
    }

    @ExceptionHandler(NetatmoUnthorizedException.class)
    ResponseEntity<String> handleUnauthorizedException(NetatmoUnthorizedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Netatmo API request unauthorized: " + exception.getMessage());
    }
}
