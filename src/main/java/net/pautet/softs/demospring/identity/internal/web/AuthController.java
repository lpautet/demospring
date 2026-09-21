package net.pautet.softs.demospring.identity.internal.web;

import lombok.AllArgsConstructor;
import net.pautet.softs.demospring.identity.RedisUserService;
import net.pautet.softs.demospring.identity.User;
import net.pautet.softs.demospring.identity.internal.security.JWTUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@AllArgsConstructor
@RequestMapping(AuthController.AUTH_ENDPOINTS_PREFIX)
public class AuthController {

    static final String AUTH_ENDPOINTS_PREFIX = "/api/auth";

    private final JWTUtil jwtUtil;
    private final RedisUserService redisUserService;

    @GetMapping("/hello")
    public String getHello() {
        return "Hello, the time at the server is now " + new Date() + "\n";
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody SignupRequest signupRequest) {
        User user = redisUserService.findByUsername(signupRequest.username());
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new AuthResponse(jwtUtil.generateToken(user.getUsername())));
    }

    @PostMapping("/signup")
    public ResponseEntity<User> signup(@RequestBody User user) {
        user.setUsername(user.getUsername());
        redisUserService.save(user);
        return ResponseEntity.ok(user);
    }
}
