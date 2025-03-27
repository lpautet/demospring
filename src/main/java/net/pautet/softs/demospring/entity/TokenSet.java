package net.pautet.softs.demospring.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class TokenSet {
    @JsonAlias("access_token")
    private String accessToken;

    @JsonAlias("expires_at")
    private long expiresAt;

    @JsonAlias("refresh_token")
    private String refreshToken;

    public void update(TokenResponse tokenResponse) {
        this.accessToken = tokenResponse.getAccessToken();
        this.refreshToken = tokenResponse.getRefreshToken();
        this.expiresAt = System.currentTimeMillis() - 60000 + tokenResponse.getExpiresIn() * 1000;
    }
}
