package net.pautet.softs.demospring.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class TokenResponse {
    @JsonAlias("access_token")
    private String accessToken;

    @JsonAlias("expires_in")
    private long expiresIn;

    @JsonAlias("refresh_token")
    private String refreshToken;
}
