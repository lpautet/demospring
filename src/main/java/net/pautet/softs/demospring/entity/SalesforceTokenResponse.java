package net.pautet.softs.demospring.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalesforceTokenResponse (
        @JsonAlias("access_token")
        String accessToken,

        @JsonAlias("expires_in")
        Long expiresIn,

        @JsonAlias("refresh_token")
        String refreshToken,

        @JsonAlias("instance_url")
        String instanceUrl,

        @JsonAlias("token_type")
        String tokenType,

        @JsonAlias("issued_token_type")
        String issuedTokenType,

        String id,

        String error,

        @JsonAlias("error_description")
        String errorDescription,

        String scope
) {
}
