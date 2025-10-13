package net.pautet.softs.demospring.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class NetatmoBadRequestException extends RuntimeException {
    public NetatmoBadRequestException(String message) {
        super(message);
    }

    public NetatmoBadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
