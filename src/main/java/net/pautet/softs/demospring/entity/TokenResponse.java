package net.pautet.softs.demospring.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class TokenResponse {
    @JsonAlias("access_token")
    private String accessToken;

    @JsonAlias("expires_in")
    private Long expiresIn;

    @JsonAlias("refresh_token")
    private String refreshToken;

    @JsonAlias("instance_url")
    private String instanceUrl;

    @JsonAlias("token_type")
    private String tokenType;

    @JsonAlias("issued_token_type")
    private String issuedTokenType;

    @JsonAlias("scope")
    private String scope;

    @JsonAlias("id")
    private String id;
}
