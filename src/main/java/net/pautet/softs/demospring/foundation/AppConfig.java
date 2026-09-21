package net.pautet.softs.demospring.foundation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppConfig (

     String redirectUri,
     String webauthnRpId,
     String webauthnOrigin,
     String adminEmail,
     String tokenEncryptionKey

) {}
