package com.nordicframtiden.api;

import com.nordicframtiden.security.jwt.JwtService;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Permission;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerBearerOnlyTest {

    @jakarta.annotation.Resource
    MockMvc mvc;

    @MockitoBean AuthenticationManager authenticationManager;
    @MockitoBean JwtService jwtService;
    @MockitoBean AppUserRepository userRepo;

    @Test
    void loginReturnsBearerTokenWithoutSettingAuthenticationCookies() throws Exception {
        AppUser user = new AppUser();
        user.setUsername("alice");
        user.setRoles(Set.of(Role.USER));
        user.setPermissions(Set.of(Permission.PEOPLE));
        when(authenticationManager.authenticate(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(any(), anyMap())).thenReturn("access-token");

        mvc.perform(post("/auth/login")
                .contentType("application/json")
                .content("""
                    {"username":"alice","password":"correct-password"}
                    """))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void logoutDoesNotManipulateAuthenticationCookies() throws Exception {
        mvc.perform(post("/auth/logout"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
