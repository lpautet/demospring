package net.pautet.softs.demospring.datacloud.internal.model;

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
