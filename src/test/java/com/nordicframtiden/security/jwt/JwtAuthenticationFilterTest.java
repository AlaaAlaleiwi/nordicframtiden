package com.nordicframtiden.security.jwt;

import jakarta.servlet.http.Cookie;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Permission;
import com.nordicframtiden.security.model.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accessTokenCookieIsNeverAcceptedAsAuthentication() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        AppUserRepository userRepository = mock(AppUserRepository.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setCookies(new Cookie("ACCESS_TOKEN", "cookie-token"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
        verifyNoInteractions(userRepository);
    }

    @Test
    void bearerTokenForDisabledAccountIsRejected() throws Exception {
        JwtService jwtService = new JwtService(
            "test-only-key-that-is-at-least-32-bytes", "test-issuer", 60);
        AppUserRepository userRepository = mock(AppUserRepository.class);
        AppUser disabled = new AppUser();
        disabled.setUsername("disabled-user");
        disabled.setEnabled(false);
        when(userRepository.findByUsername("disabled-user")).thenReturn(Optional.of(disabled));
        String token = jwtService.generateAccessToken("disabled-user", Map.of());
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void bearerTokenUsesCurrentRolesAndPermissionsFromActiveAccount() throws Exception {
        JwtService jwtService = new JwtService(
            "test-only-key-that-is-at-least-32-bytes", "test-issuer", 60);
        AppUserRepository userRepository = mock(AppUserRepository.class);
        AppUser active = new AppUser();
        active.setUsername("active-user");
        active.setEnabled(true);
        active.setRoles(Set.of(Role.STAFF));
        active.setPermissions(Set.of(Permission.SALARIES));
        when(userRepository.findByUsername("active-user")).thenReturn(Optional.of(active));
        String token = jwtService.generateAccessToken("active-user", Map.of(
            "roles", Set.of("ADMIN"),
            "perms", Set.of("PEOPLE")));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/salaries/month");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
            .extracting("authority")
            .containsExactlyInAnyOrder("ROLE_STAFF", "PERM_SALARIES");
    }
}
