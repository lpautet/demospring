package net.pautet.softs.demospring.weather.internal;

import java.io.IOException;

public class NetatmoRateLimitException extends IOException {
    public NetatmoRateLimitException(String message) {
        super(message);
    }
}
