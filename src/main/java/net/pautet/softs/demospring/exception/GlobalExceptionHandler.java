package net.pautet.softs.demospring.exception;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.NetatmoErrorResponse;
import net.pautet.softs.demospring.security.JwtExpiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(JwtExpiredException.class)
    public ResponseEntity<NetatmoErrorResponse> handleJwtExpiredException(JwtExpiredException ex) {
        log.info("JWT token has expired: {}", ex.getMessage());
        NetatmoErrorResponse error = new NetatmoErrorResponse(new NetatmoErrorResponse.Error(401, "JWT token has expired"));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(NetatmoApiException.class)
    public ResponseEntity<NetatmoErrorResponse> handleNetatmoApiException(NetatmoApiException e) {
        log.info("Netatmo exception received: " + e.getError().getError().getCode() + " " + e.getError().getError().getMessage());
        return ResponseEntity.status(e.getStatus()).body(e.getError());
    }
} 