package net.pautet.softs.demospring.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NetatmoBadRequestResponse(
        @JsonProperty("error") String error
) { }