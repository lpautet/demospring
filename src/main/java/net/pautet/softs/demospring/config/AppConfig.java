package net.pautet.softs.demospring.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@ConfigurationProperties("app")
@Configuration
public class AppConfig {

    private String redirectUri;

}
