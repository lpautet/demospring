package net.pautet.softs.demospring.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;

@Data
@RedisHash("user")
public class User implements Serializable {
    @Id
    private String username;
    private String accessToken;
    private String refreshToken;
    private Long expiresAt;
}
