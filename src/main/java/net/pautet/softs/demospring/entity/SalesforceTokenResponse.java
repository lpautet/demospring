package net.pautet.softs.demospring.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
