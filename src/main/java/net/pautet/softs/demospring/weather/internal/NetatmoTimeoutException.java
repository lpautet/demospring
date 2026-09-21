package net.pautet.softs.demospring.weather.internal;

import java.io.IOException;

public class NetatmoTimeoutException extends IOException {
    public NetatmoTimeoutException(String message) {
        super(message);
    }

    public NetatmoTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
