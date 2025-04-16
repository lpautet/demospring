package net.pautet.softs.demospring.exception;

import net.pautet.softs.demospring.entity.NetatmoErrorResponse;
import org.springframework.http.HttpStatus;

public class NetatmoApiException extends RuntimeException {
    private final NetatmoErrorResponse error;
    private final HttpStatus status;

    public NetatmoApiException(NetatmoErrorResponse error, HttpStatus status) {
        super(error.getError().getMessage());
        this.error = error;
        this.status = status;
    }

    public NetatmoErrorResponse getError() {
        return error;
    }

    public HttpStatus getStatus() {
        return status;
    }
} 