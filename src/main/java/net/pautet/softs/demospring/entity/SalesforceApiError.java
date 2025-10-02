package net.pautet.softs.demospring.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalesforceApiError(String error, @JsonProperty("error_description") String errorDescription) {
}
