package net.pautet.softs.demospring.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record NetatmoTokenResponse(
        @JsonAlias("access_token")
        String accessToken,

        @JsonAlias("expires_in")
        Long expiresIn,

        @JsonAlias("refresh_token")
        String refreshToken,

        String[] scope
) {
}
