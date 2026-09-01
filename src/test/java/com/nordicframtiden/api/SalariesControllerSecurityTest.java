package com.nordicframtiden.api;

import com.nordicframtiden.company.StaffShiftRepository;
import com.nordicframtiden.pharmacy.ScheduleShiftRepository;
import com.nordicframtiden.security.jwt.JwtService;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.UserProfile;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import com.nordicframtiden.service.PayrollService;
import com.nordicframtiden.service.model.NetSalaryResponse;
import com.nordicframtiden.settings.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SalariesController.class)
@AutoConfigureMockMvc
@Import(SalariesControllerSecurityTest.MethodSecurityTestConfig.class)
class SalariesControllerSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @jakarta.annotation.Resource
    MockMvc mvc;

    @MockitoBean ScheduleShiftRepository shiftRepo;
    @MockitoBean StaffShiftRepository staffShiftRepo;
    @MockitoBean UserProfileRepository profileRepo;
    @MockitoBean AppUserRepository userRepo;
    @MockitoBean PayrollService payrollService;
    @MockitoBean EmailService emailService;
    @MockitoBean JwtService jwtService;

    @Test
    @WithMockUser(roles = "USER")
    void userCannotReadAnotherUsersPayslip() throws Exception {
        mvc.perform(get("/api/salaries/payslip")
                .param("userId", "42").param("year", "2026").param("month", "8"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotReadCompanySalaryReports() throws Exception {
        mvc.perform(get("/api/salaries/month")
                .param("start", "2026-08-01T00:00:00Z")
                .param("end", "2026-09-01T00:00:00Z"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotReadAnotherUsersSalaryHistory() throws Exception {
        mvc.perform(get("/api/salaries/user/years").param("userId", "42"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotSendSalaryEmail() throws Exception {
        mvc.perform(post("/api/salaries/send-pdf-email").with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "userId": 42,
                      "year": 2026,
                      "month": 8,
                      "role": "USER",
                      "pdfBase64": "cGRm"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void userCanReadOwnPayslipWithoutSupplyingUserId() throws Exception {
        AppUser alice = mock(AppUser.class);
        when(alice.getId()).thenReturn(7L);
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(payrollService.netSalaryForUserMonth(7L, 2026, 8)).thenReturn(payslip(7L));

        mvc.perform(get("/api/salaries/payslip/me")
                .param("year", "2026").param("month", "8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(7));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void userCanReadOwnSalaryHistoryWithoutSupplyingUserId() throws Exception {
        AppUser alice = mock(AppUser.class);
        when(alice.getId()).thenReturn(7L);
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(shiftRepo.findInRange(any(), any(), eq(null), eq(7L))).thenReturn(List.of());

        mvc.perform(get("/api/salaries/me/years"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        mvc.perform(get("/api/salaries/me/months").param("year", "2026"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        mvc.perform(get("/api/salaries/me/month")
                .param("year", "2026").param("month", "8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STAFF", "PERM_PEOPLE"})
    void staffWithoutSalaryPermissionCannotReadReports() throws Exception {
        mvc.perform(get("/api/salaries/month")
                .param("start", "2026-08-01T00:00:00Z")
                .param("end", "2026-09-01T00:00:00Z"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STAFF", "PERM_SALARIES"})
    void staffWithSalaryPermissionCanReadReports() throws Exception {
        when(shiftRepo.findInRange(any(), any(), eq(null), eq(null))).thenReturn(List.of());

        mvc.perform(get("/api/salaries/month")
                .param("start", "2026-08-01T00:00:00Z")
                .param("end", "2026-09-01T00:00:00Z"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void salaryEmailUsesStoredEmployeeIdentityInsteadOfCallerSuppliedRecipient() throws Exception {
        UserProfile profile = new UserProfile();
        profile.setEmail("employee@example.com");
        profile.setFullName("Stored Employee");
        when(profileRepo.findByUserId(42L)).thenReturn(Optional.of(profile));
        when(payrollService.netSalaryForUserMonth(42L, 2026, 8)).thenReturn(payslip(42L));
        when(emailService.sendSalaryPdfEmail(eq("employee@example.com"), eq("Stored Employee"), any(), eq("2026-08")))
            .thenReturn(true);

        mvc.perform(post("/api/salaries/send-pdf-email").with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "userId": 42,
                      "year": 2026,
                      "month": 8,
                      "role": "USER",
                      "email": "attacker@example.com",
                      "employeeName": "Attacker",
                      "pdfBase64": "cGRm"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recipient").value("employee@example.com"))
            .andExpect(jsonPath("$.employeeName").value("Stored Employee"));

        verify(emailService).sendSalaryPdfEmail(
            eq("employee@example.com"), eq("Stored Employee"), any(), eq("2026-08"));
    }

    private static NetSalaryResponse payslip(long userId) {
        return new NetSalaryResponse(
            userId, "2026-08", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            2026, "0180", 30, 1, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
