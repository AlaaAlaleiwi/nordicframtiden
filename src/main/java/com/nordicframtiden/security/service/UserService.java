package com.nordicframtiden.security.service;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Permission;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.model.UserProfile;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import com.nordicframtiden.settings.EmailService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Year;
import java.util.List;
import java.util.Set;

@Service
public class UserService {

  private final AppUserRepository userRepo;
  private final UserProfileRepository profileRepo;
  private final PasswordEncoder encoder;
  private final EmailService emailService;

  public UserService(AppUserRepository userRepo, UserProfileRepository profileRepo, PasswordEncoder encoder, EmailService emailService) {
    this.userRepo = userRepo;
    this.profileRepo = profileRepo;
    this.encoder = encoder;
    this.emailService = emailService;
  }

  // ---------- Records ----------

  public record UserRow(
      Long id,
      String username,
      boolean enabled,
      String fullName,
      String email,
      String phone,
      BigDecimal hourlyCost,
      Integer yearOfBirth,
      String countyCode,
      String municipalityCode,
      Set<Permission> permissions,
      String password
  ) {}

  public record DetailedUser(
      Long id,
      String username,
      boolean enabled,
      String fullName,
      String email,
      String phone,
      BigDecimal hourlyCost,
      Integer yearOfBirth,
      String countyCode,
      String municipalityCode,
      Set<Permission> permissions
  ) {}

  // ---------- Validation helpers ----------

  private static void validateYear(Integer yearOfBirth) {
    if (yearOfBirth == null) throw new IllegalArgumentException("yearOfBirth is required");
    int now = Year.now().getValue();
    if (yearOfBirth < 1900 || yearOfBirth > now) throw new IllegalArgumentException("Invalid yearOfBirth");
  }

  private static void validateCodes(String countyCode, String municipalityCode) {
    if (countyCode == null || countyCode.isBlank()) throw new IllegalArgumentException("countyCode is required");
    if (municipalityCode == null || municipalityCode.isBlank()) throw new IllegalArgumentException("municipalityCode is required");

    String cc = countyCode.trim();
    String mc = municipalityCode.trim();

    if (!cc.matches("\\d{2}")) throw new IllegalArgumentException("Invalid countyCode");
    if (!mc.matches("\\d{4}")) throw new IllegalArgumentException("Invalid municipalityCode");

    if (!mc.startsWith(cc)) throw new IllegalArgumentException("municipalityCode must start with countyCode");
  }

  private static Set<Permission> safePerms(Set<Permission> perms) {
    return perms == null ? Set.of() : perms;
  }

  // ---------- Read ----------

  public UserProfile getProfileByUserId(Long userId) {
    return profileRepo.findByUserId(userId)
        .orElseThrow(() -> new IllegalArgumentException("Profile not found"));
  }

  public DetailedUser getDetailedByUsername(String username) {
    AppUser u = userRepo.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    UserProfile profile = profileRepo.findByUserId(u.getId()).orElse(null);

    return new DetailedUser(
        u.getId(),
        u.getUsername(),
        u.isEnabled(),
        profile != null ? profile.getFullName() : u.getUsername(),
        profile != null ? profile.getEmail() : null,
        profile != null ? profile.getPhone() : null,
        profile != null ? profile.getHourlyCost() : null,
        profile != null ? profile.getYearOfBirth() : null,
        profile != null ? profile.getCountyCode() : null,
        profile != null ? profile.getMunicipalityCode() : null,
        safePerms(u.getPermissions())
    );
  }

  public DetailedUser getDetailedById(Long id) {
    return userRepo.findById(id)
        .map(u -> {
          UserProfile profile = profileRepo.findByUserId(u.getId()).orElse(null);
          return new DetailedUser(
              u.getId(),
              u.getUsername(),
              u.isEnabled(),
              profile != null ? profile.getFullName() : u.getUsername(),
              profile != null ? profile.getEmail() : null,
              profile != null ? profile.getPhone() : null,
              profile != null ? profile.getHourlyCost() : null,
              profile != null ? profile.getYearOfBirth() : null,
              profile != null ? profile.getCountyCode() : null,
              profile != null ? profile.getMunicipalityCode() : null,
              safePerms(u.getPermissions())
          );
        })
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  public List<UserRow> listDetailedByRole(Role role) {
    return userRepo.findAllByRole(role).stream().map(u -> {
      UserProfile p = profileRepo.findByUserId(u.getId()).orElse(null);
      return new UserRow(
          u.getId(),
          u.getUsername(),
          u.isEnabled(),
          p != null ? p.getFullName() : null,
          p != null ? p.getEmail() : null,
          p != null ? p.getPhone() : null,
          p != null ? p.getHourlyCost() : null,
          p != null ? p.getYearOfBirth() : null,
          p != null ? p.getCountyCode() : null,
          p != null ? p.getMunicipalityCode() : null,
          safePerms(u.getPermissions()),
          null
      );
    }).toList();
  }

  // ---------- Create ----------

  @Transactional
  public UserRow createWithProfile(
      Role role,
      boolean enabled,
      String fullName,
      String email,
      String phone,
      BigDecimal hourlyCost,
      Integer yearOfBirth,
      String countyCode,
      String municipalityCode,
      Set<Permission> permissions // ✅ NEW PARAM
  ) {

    if (role != Role.USER && role != Role.STAFF) {
      throw new IllegalArgumentException("Only USER or STAFF can be created here");
    }

    if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("fullName is required");
    if (email == null || email.isBlank()) throw new IllegalArgumentException("email is required");
    if (phone == null || phone.isBlank()) throw new IllegalArgumentException("phone is required");

    validateYear(yearOfBirth);
    validateCodes(countyCode, municipalityCode);

    if (profileRepo.existsByEmail(email)) throw new IllegalArgumentException("Email already exists");
    if (profileRepo.existsByPhone(phone)) throw new IllegalArgumentException("Phone already exists");

    String username = generateUniqueUsername(fullName);
    String rawPassword = generatePassword(12);

    AppUser u = new AppUser();
    u.setUsername(username);
    u.setPasswordHash(encoder.encode(rawPassword));
    u.setEnabled(enabled);
    u.setRoles(Set.of(role));

    // ✅ Permissions only matter for STAFF
    if (role == Role.STAFF) u.setPermissions(safePerms(permissions));
    else u.setPermissions(Set.of());

    u = userRepo.save(u);

    UserProfile p = new UserProfile();
    p.setFullName(fullName.trim());
    p.setEmail(email.trim());
    p.setPhone(phone.trim());
    p.setHourlyCost(hourlyCost);

    p.setYearOfBirth(yearOfBirth);
    p.setCountyCode(countyCode.trim());
    p.setMunicipalityCode(municipalityCode.trim());

    p.setUser(u);
    profileRepo.save(p);

    return new UserRow(
        u.getId(),
        u.getUsername(),
        u.isEnabled(),
        p.getFullName(),
        p.getEmail(),
        p.getPhone(),
        p.getHourlyCost(),
        p.getYearOfBirth(),
        p.getCountyCode(),
        p.getMunicipalityCode(),
        safePerms(u.getPermissions()),
        rawPassword
    );
  }

  // ---------- Update ----------

  @Transactional
  public UserRow updateWithProfile(
      Long id,
      String fullName,
      String email,
      String phone,
      Boolean enabled,
      BigDecimal hourlyCost,
      Integer yearOfBirth,
      String countyCode,
      String municipalityCode,
      Set<Permission> permissions // ✅ NEW PARAM
  ) {

    AppUser u = userRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (enabled != null) u.setEnabled(enabled);

    UserProfile p = profileRepo.findByUserId(id)
        .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

    if (fullName != null && !fullName.isBlank()) p.setFullName(fullName.trim());

    if (email != null && !email.isBlank() && !email.equalsIgnoreCase(p.getEmail())) {
      if (profileRepo.existsByEmail(email)) throw new IllegalArgumentException("Email already exists");
      p.setEmail(email.trim());
    }

    if (phone != null && !phone.isBlank() && !phone.equals(p.getPhone())) {
      if (profileRepo.existsByPhone(phone)) throw new IllegalArgumentException("Phone already exists");
      p.setPhone(phone.trim());
    }

    if (hourlyCost != null) p.setHourlyCost(hourlyCost);

    if (yearOfBirth != null) {
      validateYear(yearOfBirth);
      p.setYearOfBirth(yearOfBirth);
    }

    boolean anyCodeProvided =
        (countyCode != null && !countyCode.isBlank()) ||
        (municipalityCode != null && !municipalityCode.isBlank());

    if (anyCodeProvided) {
      String nextCounty = (countyCode == null || countyCode.isBlank()) ? p.getCountyCode() : countyCode.trim();
      String nextMunicipality = (municipalityCode == null || municipalityCode.isBlank()) ? p.getMunicipalityCode() : municipalityCode.trim();
      validateCodes(nextCounty, nextMunicipality);
      p.setCountyCode(nextCounty);
      p.setMunicipalityCode(nextMunicipality);
    }

    // ✅ Update permissions only if this user is STAFF and request includes permissions
    boolean isStaff = u.getRoles() != null && u.getRoles().contains(Role.STAFF);
    if (isStaff && permissions != null) {
      u.setPermissions(safePerms(permissions));
    }

    profileRepo.save(p);
    userRepo.save(u);

    return new UserRow(
        u.getId(),
        u.getUsername(),
        u.isEnabled(),
        p.getFullName(),
        p.getEmail(),
        p.getPhone(),
        p.getHourlyCost(),
        p.getYearOfBirth(),
        p.getCountyCode(),
        p.getMunicipalityCode(),
        safePerms(u.getPermissions()),
        null
    );
  }

  // ---------- Delete ----------

  @Transactional
  public void deleteUser(Long id) {
    AppUser u = userRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (u.getRoles() != null && u.getRoles().contains(Role.ADMIN)) {
      throw new IllegalArgumentException("Cannot delete ADMIN from /api/users");
    }

    profileRepo.findByUserId(id).ifPresent(profileRepo::delete);
    userRepo.delete(u);
  }

  // ---------- Reset Password ----------

  @Transactional
  public UserRow resetPassword(Long id) {
    AppUser u = userRepo.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (u.getRoles() != null && u.getRoles().contains(Role.ADMIN)) {
      throw new IllegalArgumentException("Cannot reset ADMIN password from /api/users");
    }

    String rawPassword = generatePassword(12);
    u.setPasswordHash(encoder.encode(rawPassword));
    userRepo.save(u);

    UserProfile p = profileRepo.findByUserId(u.getId()).orElse(null);
    if (p != null && p.getEmail() != null && !p.getEmail().isBlank()) {
      emailService.sendPasswordResetEmail(p.getEmail(), u.getUsername(), rawPassword);
    }

    return new UserRow(
        u.getId(),
        u.getUsername(),
        u.isEnabled(),
        p != null ? p.getFullName() : null,
        p != null ? p.getEmail() : null,
        p != null ? p.getPhone() : null,
        p != null ? p.getHourlyCost() : null,
        p != null ? p.getYearOfBirth() : null,
        p != null ? p.getCountyCode() : null,
        p != null ? p.getMunicipalityCode() : null,
        safePerms(u.getPermissions()),
        rawPassword
    );
  }

  // ---------- Username + Password helpers ----------

  private String generateUniqueUsername(String fullName) {
    String base = (fullName == null ? "user" : fullName)
        .trim()
        .toLowerCase()
        .replaceAll("[^a-z0-9]+", ".")
        .replaceAll("^\\.|\\.$", "");

    if (base.isBlank()) base = "user";

    String candidate = base;
    int i = 1;
    while (userRepo.existsByUsername(candidate)) {
      i++;
      candidate = base + i;
    }
    return candidate;
  }

  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
  private static final SecureRandom RNG = new SecureRandom();

  private String generatePassword(int len) {
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }
}