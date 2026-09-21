package net.pautet.softs.demospring.identity.internal.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.pautet.softs.demospring.identity.RedisUserService;
import net.pautet.softs.demospring.identity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth/passkey")
public class PasskeyController {

    public static final String EXPECTED_EMAIL = PasskeyController.class.getName() + ".expectedEmail";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final RedisUserService users;
    private final PublicKeyCredentialUserEntityRepository userEntities;
    private final UserCredentialRepository credentials;
    private final HttpSessionSecurityContextRepository securityContexts = new HttpSessionSecurityContextRepository();

    PasskeyController(RedisUserService users,
                      PublicKeyCredentialUserEntityRepository userEntities,
                      UserCredentialRepository credentials) {
        this.users = users;
        this.userEntities = userEntities;
        this.credentials = credentials;
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/begin")
    ResponseEntity<BeginResponse> begin(@RequestBody BeginRequest body,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        String email = normalize(body.email());
        if (!EMAIL.matcher(email).matches()) {
            return ResponseEntity.badRequest().build();
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(EXPECTED_EMAIL, email);
        PublicKeyCredentialUserEntity entity = userEntities.findByUsername(email);
        boolean registered = entity != null && !credentials.findByUserId(entity.getId()).isEmpty();
        if (registered) {
            Authentication lookup = UsernamePasswordAuthenticationToken.authenticated(
                    email, null, List.of(new SimpleGrantedAuthority("ROLE_PASSKEY_AUTHENTICATION")));
            saveAuthentication(lookup, request, response);
            return ResponseEntity.ok(new BeginResponse("AUTHENTICATE", email));
        }

        if (users.findByUsername(email) == null) {
            User user = new User();
            user.setUsername(email);
            users.save(user);
        }
        Authentication registration = UsernamePasswordAuthenticationToken.authenticated(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_PASSKEY_REGISTRATION")));
        saveAuthentication(registration, request, response);
        return ResponseEntity.ok(new BeginResponse("REGISTER", email));
    }

    @PostMapping("/complete-registration")
    ResponseEntity<BeginResponse> completeRegistration(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        String email = session == null ? null : (String) session.getAttribute(EXPECTED_EMAIL);
        PublicKeyCredentialUserEntity entity = email == null ? null : userEntities.findByUsername(email);
        if (entity == null || credentials.findByUserId(entity.getId()).isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        UserDetails user = users.loadUserByUsername(email);
        request.changeSessionId();
        saveAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()), request, response);
        request.getSession().removeAttribute(EXPECTED_EMAIL);
        return ResponseEntity.ok(new BeginResponse("AUTHENTICATED", email));
    }

    private void saveAuthentication(Authentication authentication,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContexts.saveContext(context, request, response);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    record BeginRequest(String email) {}
    record BeginResponse(String action, String email) {}
    record CsrfResponse(String token, String headerName) {}
}
