package net.pautet.softs.demospring.entity;

import com.fasterxml.jackson.annotation.JsonAlias;

public record SalesforceTokenResponse (
        @JsonAlias("access_token")
        String accessToken,

        @JsonAlias("instance_url")
        String instanceUrl,

        @JsonAlias("token_type")
        String tokenType,

        String id,

        String scope
) {
}
