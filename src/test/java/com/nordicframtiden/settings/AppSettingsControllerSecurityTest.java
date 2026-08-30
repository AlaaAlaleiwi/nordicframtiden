package com.nordicframtiden.settings;

import com.nordicframtiden.security.jwt.JwtService;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppSettingsController.class)
@AutoConfigureMockMvc
@Import(AppSettingsControllerSecurityTest.MethodSecurityTestConfig.class)
class AppSettingsControllerSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @jakarta.annotation.Resource
    MockMvc mvc;

    @MockitoBean AppSettingsService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean AppUserRepository userRepository;

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotReadSmtpConfiguration() throws Exception {
        mvc.perform(get("/api/settings/mail"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotChangeSmtpConfiguration() throws Exception {
        mvc.perform(put("/api/settings/mail").with(csrf())
                .contentType("application/json")
                .content("""
                    {"enabled":true,"host":"smtp.example.com","port":587}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminResponseContainsOnlyPasswordConfiguredIndicator() throws Exception {
        when(service.getMailSettings()).thenReturn(Map.of(
            "enabled", "true",
            "passwordConfigured", "true"
        ));

        mvc.perform(get("/api/settings/mail"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordConfigured").value("true"));
    }
}
