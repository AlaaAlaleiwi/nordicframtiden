package com.nordicframtiden.api;

import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.jwt.JwtService;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserManagementController.class)
@AutoConfigureMockMvc
@Import(UserManagementControllerSecurityTest.MethodSecurityTestConfig.class)
class UserManagementControllerSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @jakarta.annotation.Resource
    MockMvc mvc;

    @MockitoBean
    UserService userService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    AppUserRepository userRepository;

    @Test
    @WithMockUser(roles = "USER")
    void userCannotListAccounts() throws Exception {
        mvc.perform(get("/api/users").param("role", "USER"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STAFF", "PERM_PEOPLE"})
    void staffWithPeoplePermissionCanListUsers() throws Exception {
        when(userService.listDetailedByRole(Role.USER)).thenReturn(List.of());

        mvc.perform(get("/api/users").param("role", "USER"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STAFF", "PERM_PEOPLE"})
    void staffCannotListStaffAccounts() throws Exception {
        mvc.perform(get("/api/users").param("role", "STAFF"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotResetAnotherAccountPassword() throws Exception {
        mvc.perform(post("/api/users/{id}/reset-password", 42L).with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void userCanUpdateOnlySafeOwnProfileFields() throws Exception {
        when(userService.updateOwnProfile(
            "alice", "Alice", "alice@example.com", "0700000000", 1990, "01", "0180"))
            .thenReturn(new UserService.UserRow(
                7L, "alice", true, "Alice", "alice@example.com", "0700000000",
                null, 1990, "01", "0180", Set.of(), null));

        mvc.perform(put("/api/users/me").with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "fullName": "Alice",
                      "email": "alice@example.com",
                      "phone": "0700000000",
                      "yearOfBirth": 1990,
                      "countyCode": "01",
                      "municipalityCode": "0180"
                    }
                    """))
            .andExpect(status().isOk());
    }
}
