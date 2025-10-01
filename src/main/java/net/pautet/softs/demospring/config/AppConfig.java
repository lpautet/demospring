package net.pautet.softs.demospring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppConfig (

     String redirectUri,
     String jwtSecret

) {}
