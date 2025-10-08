package net.pautet.softs.demospring.entity;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record NetatmoTokenResponse(
        @JsonAlias("access_token")
        String accessToken,

        @JsonAlias("expires_in")
        Long expiresIn,

        @JsonAlias("refresh_token")
        String refreshToken,

        List<String> scope
) {
}
