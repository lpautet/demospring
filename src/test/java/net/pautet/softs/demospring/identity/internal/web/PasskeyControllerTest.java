package net.pautet.softs.demospring.identity.internal.web;

import net.pautet.softs.demospring.identity.RedisUserService;
import net.pautet.softs.demospring.identity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasskeyControllerTest {

    private final RedisUserService users = mock(RedisUserService.class);
    private final PublicKeyCredentialUserEntityRepository userEntities = mock(PublicKeyCredentialUserEntityRepository.class);
    private final UserCredentialRepository credentials = mock(UserCredentialRepository.class);
    private final PasskeyController controller = new PasskeyController(users, userEntities, credentials);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void newEmailCreatesARegistrationOnlySession() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = controller.begin(new PasskeyController.BeginRequest(" New@Example.com "),
                request, new MockHttpServletResponse());

        assertThat(response.getBody()).isEqualTo(new PasskeyController.BeginResponse("REGISTER", "new@example.com"));
        assertThat(authentication(request).getName()).isEqualTo("new@example.com");
        assertThat(authentication(request).getAuthorities()).extracting("authority")
                .containsExactly("ROLE_PASSKEY_REGISTRATION");
        verify(users).save(argThat(user -> user.getUsername().equals("new@example.com")));
    }

    @Test
    void registeredEmailCreatesOnlyAPasskeyLookupSession() {
        PublicKeyCredentialUserEntity entity = mock(PublicKeyCredentialUserEntity.class);
        Bytes userId = mock(Bytes.class);
        when(entity.getId()).thenReturn(userId);
        when(userEntities.findByUsername("known@example.com")).thenReturn(entity);
        when(credentials.findByUserId(userId)).thenReturn(List.of(mock(CredentialRecord.class)));
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = controller.begin(new PasskeyController.BeginRequest("known@example.com"),
                request, new MockHttpServletResponse());

        assertThat(response.getBody()).isEqualTo(new PasskeyController.BeginResponse("AUTHENTICATE", "known@example.com"));
        assertThat(authentication(request).getAuthorities()).extracting("authority")
                .containsExactly("ROLE_PASSKEY_AUTHENTICATION");
        verify(users, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void rejectsMalformedEmailBeforeCreatingAUser() {
        var response = controller.begin(new PasskeyController.BeginRequest("not-an-email"),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(users, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    private static org.springframework.security.core.Authentication authentication(MockHttpServletRequest request) {
        SecurityContext context = (SecurityContext) request.getSession().getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return context.getAuthentication();
    }
}
