package net.pautet.softs.demospring.weather.internal.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import net.pautet.softs.demospring.weather.NetatmoTokenResponse;

@Data
public class TokenSet {
    @JsonAlias("access_token")
    private String accessToken;

    @JsonAlias("expires_at")
    private long expiresAt;

    @JsonAlias("refresh_token")
    private String refreshToken;

    public void update(NetatmoTokenResponse tokenResponse) {
        this.accessToken = tokenResponse.accessToken();
        this.refreshToken = tokenResponse.refreshToken();
        this.expiresAt = System.currentTimeMillis() - 60000 + tokenResponse.expiresIn() * 1000;
    }
}
