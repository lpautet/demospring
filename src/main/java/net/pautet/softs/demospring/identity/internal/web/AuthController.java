package net.pautet.softs.demospring.identity.internal.web;

import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping(AuthController.AUTH_ENDPOINTS_PREFIX)
public class AuthController {

    static final String AUTH_ENDPOINTS_PREFIX = "/api/auth";

    @GetMapping("/hello")
    public String getHello() {
        return "Hello, the time at the server is now " + new Date() + "\n";
    }

}
