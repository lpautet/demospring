package net.pautet.softs.demospring.identity.internal.web;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.identity.internal.security.JwtExpiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
class IdentityExceptionHandler {

    @ExceptionHandler(JwtExpiredException.class)
    ResponseEntity<ApiErrorResponse> handleJwtExpiredException(JwtExpiredException exception) {
        log.warn("JWT token has expired: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse(new ApiError(401, "JWT token has expired")));
    }

    private record ApiErrorResponse(ApiError error) {
    }

    private record ApiError(int code, String message) {
    }
}
