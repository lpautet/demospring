package net.pautet.softs.demospring.weather.internal.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NetatmoBadRequestResponse(
        @JsonProperty("error") String error
) { }