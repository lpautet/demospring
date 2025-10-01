package net.pautet.softs.demospring.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("user")
public class User implements Serializable {
    @Id
    private String username;
    private String accessToken;
    private String refreshToken;
    private Long expiresAt;
}
