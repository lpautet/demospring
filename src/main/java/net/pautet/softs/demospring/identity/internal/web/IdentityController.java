package net.pautet.softs.demospring.identity.internal.web;

import net.pautet.softs.demospring.identity.RedisUserService;
import net.pautet.softs.demospring.identity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api")
class IdentityController {

    private final RedisUserService redisUserService;

    IdentityController(RedisUserService redisUserService) {
        this.redisUserService = redisUserService;
    }

    @GetMapping("/whoami")
    ResponseEntity<User> getWhoAmI(Principal principal) {
        User user = redisUserService.findByUsername(principal.getName());
        return user == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(user);
    }
}
