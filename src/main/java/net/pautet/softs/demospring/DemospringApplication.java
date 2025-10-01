package net.pautet.softs.demospring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@ConfigurationPropertiesScan("net.pautet.softs.demospring.config")
public class DemospringApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemospringApplication.class, args);
	}

}
