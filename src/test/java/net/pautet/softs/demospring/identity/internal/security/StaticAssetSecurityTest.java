package net.pautet.softs.demospring.identity.internal.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StaticAssetSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping requestMappings;

    @MockitoBean(answers = RETURNS_DEEP_STUBS)
    private StringRedisTemplate redisTemplate;

    @Test
    void viteAssetsArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/assets/test.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
    }

    @Test
    void apiEndpointsRemainProtected() throws Exception {
        mockMvc.perform(get("/api/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void systemNetatmoAuthorizationRequiresAnAdministrator() throws Exception {
        mockMvc.perform(get("/api/netatmo/authorize"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/netatmo/authorize").with(user("person@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/netatmo/authorize").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void passkeyBootstrapIsPublicButStateChangesRequireCsrf() throws Exception {
        mockMvc.perform(get("/api/auth/passkey/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));

        mockMvc.perform(post("/api/auth/passkey/begin")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/passkey/begin")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void capabilityControllersPreserveExistingApiPaths() {
        Set<String> paths = requestMappings.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .collect(Collectors.toSet());

        org.assertj.core.api.Assertions.assertThat(paths).contains(
                "/api/messages",
                "/api/whoami",
                "/api/homesdata",
                "/api/homestatus",
                "/api/getmeasure",
                "/api/salesforce/accounts",
                "/api/datacloud/data",
                "/api/salesforce/whoami",
                "/api/netatmo/authorize",
                "/api/netatmo/callback",
                "/api/auth/hello",
                "/api/auth/passkey/csrf",
                "/api/auth/passkey/begin",
                "/api/auth/passkey/complete-registration",
                "/api/auth/authorizeAtmo",
                "/api/auth/callbackAtmo");
    }
}
