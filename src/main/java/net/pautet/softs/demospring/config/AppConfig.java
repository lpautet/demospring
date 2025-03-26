package net.pautet.softs.demospring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties("app")
@Configuration
public class AppConfig {

    private String redirectUri;
    private String jwtSecret;

}
