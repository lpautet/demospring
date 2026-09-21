package net.pautet.softs.demospring.weather.internal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NetatmoErrorResponse(
        @JsonProperty("error") Error error
) {
    public record Error(
        @JsonProperty("code")
         int code,

        @JsonProperty("message")
        String message
    ) {}
}