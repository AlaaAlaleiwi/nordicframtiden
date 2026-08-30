package com.nordicframtiden.admin;

import com.nordicframtiden.admin.model.AdminProfile;
import com.nordicframtiden.admin.model.AdminProfileRepository;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.settings.EmailService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {

    private final AppUserRepository repo;
    private final AdminProfileRepository adminProfileRepo;
    private final PasswordEncoder encoder;
    private final EmailService emailService;

    public AdminService(AppUserRepository repo,
                       AdminProfileRepository adminProfileRepo,
                       PasswordEncoder encoder,
                       EmailService emailService) {
        this.repo = repo;
        this.adminProfileRepo = adminProfileRepo;
        this.encoder = encoder;
        this.emailService = emailService;
    }

    public record AdminRow(
            Long id,
            String username,
            boolean enabled,
            String fullName,
            String email,
            String phone,
            String password) {
    }

    /* =========================
       LIST (with profile fields)
       ========================= */
    @Transactional(readOnly = true)
    public List<AdminRow> listAdminsDetailed() {
        return repo.findAllAdmins().stream()
                .map(u -> {
                    AdminProfile p = adminProfileRepo.findByUserId(u.getId()).orElse(null);
                    return new AdminRow(
                            u.getId(),
                            u.getUsername(),
                            u.isEnabled(),
                            p != null ? p.getFullName() : null,
                            p != null ? p.getEmail() : null,
                            p != null ? p.getPhone() : null,
                            null);
                })
                .toList();
    }

    /* =========================
       CREATE (password generated)
       ========================= */
    @Transactional
    public AdminRow createAdminWithProfile(
            boolean enabled,
            String fullName,
            String email,
            String phone) {

        if (adminProfileRepo.existsByEmail(email))
            throw new IllegalArgumentException("Email already exists");

        if (adminProfileRepo.existsByPhone(phone))
            throw new IllegalArgumentException("Phone already exists");

        // 1️⃣ Generate username
        String username = generateUniqueUsername(fullName);
        String password = generatePassword();

        // 2️⃣ Create login user
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setEnabled(enabled);
        // Assign the ADMIN role (the missing/unfinished code)
        user.setRoles(Set.of(Role.ADMIN));

        repo.save(user);

        // 3️⃣ Create profile linked to the user
        AdminProfile profile = new AdminProfile();
        profile.setFullName(fullName);
        profile.setEmail(email);
        profile.setPhone(phone);
        profile.setUser(user);
        adminProfileRepo.save(profile);

        return new AdminRow(
                user.getId(),
                user.getUsername(),
                user.isEnabled(),
                fullName,
                email,
                phone,
                password);
    }

    /* =========================
       HELPERS
       ========================= */
    private String generateUniqueUsername(String fullName) {
        // Simple deterministic username generation – can be replaced with a more robust algorithm
        String base = fullName.toLowerCase().replaceAll("\\s+", ".");
        String candidate = base;
        int suffix = 1;
        while (repo.existsByUsername(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }

    private String generatePassword() {
        // Generate a random 12‑character password using SecureRandom and Base64
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[9]; // 9 bytes -> 12 Base64 chars (without padding)
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /* =========================
       READ (single admin)
       ========================= */
    @Transactional(readOnly = true)
    public AdminRow getDetailedUser(String username) {
        AppUser user = repo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        AdminProfile profile = adminProfileRepo.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));
        return new AdminRow(
                user.getId(),
                user.getUsername(),
                user.isEnabled(),
                profile.getFullName(),
                profile.getEmail(),
                profile.getPhone(),
                null);
    }

    @Transactional
    public AdminRow resetAdminPassword(Long id) {
        AppUser user = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String rawPassword = generatePassword();
        user.setPasswordHash(encoder.encode(rawPassword));
        repo.save(user);

        AdminProfile profile = adminProfileRepo.findByUserId(user.getId()).orElse(null);
        if (profile != null && profile.getEmail() != null && !profile.getEmail().isBlank()) {
            emailService.sendPasswordResetEmail(profile.getEmail(), user.getUsername(), rawPassword);
        }

        return new AdminRow(
                user.getId(),
                user.getUsername(),
                user.isEnabled(),
                profile != null ? profile.getFullName() : null,
                profile != null ? profile.getEmail() : null,
                profile != null ? profile.getPhone() : null,
                rawPassword);
    }
}
