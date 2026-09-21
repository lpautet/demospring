package net.pautet.softs.demospring.weather.internal;

import java.io.IOException;

public class NetatmoUnthorizedException extends IOException {
    public NetatmoUnthorizedException(String reason) {
        super(reason);
    }
}
