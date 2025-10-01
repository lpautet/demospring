package net.pautet.softs.demospring.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

public record NetatmoErrorResponse(
        @JsonProperty("error") Error error
) {
    public NetatmoErrorResponse(int code, String message) {
        this (new Error(code, message));
    }

    public record Error(
        @JsonProperty("code")
         int code,
        
        @JsonProperty("message")
        String message
    ) {}
} 