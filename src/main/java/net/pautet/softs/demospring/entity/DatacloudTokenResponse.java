package net.pautet.softs.demospring.entity;

import com.fasterxml.jackson.annotation.JsonAlias;

public record DatacloudTokenResponse(
        @JsonAlias("access_token")
        String accessToken,

        @JsonAlias("expires_in")
        Long expiresIn,

        @JsonAlias("instance_url")
        String instanceUrl,

        @JsonAlias("token_type")
        String tokenType,

        @JsonAlias("issued_token_type")
        String issuedTokenType
) {
}
