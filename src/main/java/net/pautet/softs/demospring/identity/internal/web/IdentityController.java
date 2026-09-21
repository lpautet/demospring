package net.pautet.softs.demospring.identity.internal.web;

import net.pautet.softs.demospring.identity.UserService;
import net.pautet.softs.demospring.identity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api")
class IdentityController {

    private final UserService userService;

    IdentityController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/whoami")
    ResponseEntity<CurrentUser> getWhoAmI(Principal principal) {
        User user = userService.findByUsername(principal.getName());
        return user == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(new CurrentUser(user.getUsername()));
    }

    record CurrentUser(String username) {
    }
}
