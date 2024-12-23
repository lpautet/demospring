package net.pautet.softs.demospring.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Date;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/hello")
    public Mono<String> getHello() {
        return Mono.just("Hello, the time at the server is now " + new Date() + "\n");
    }
}
