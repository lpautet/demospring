package net.pautet.softs.demospring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties("netatmo")
@Configuration
public class NetatmoConfig {

    private String clientId;
    private String clientSecret;
    private String redirectUri;

}
