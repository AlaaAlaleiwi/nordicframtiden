package com.nordicframtiden.documents;

import com.nordicframtiden.security.AccountAuthorization;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.security.repo.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

/**
 * Encrypted document storage on user profiles.
 *
 * Admins manage documents on any pharmacist/staff profile; users with
 * PERM_PEOPLE manage documents on the pharmacist/staff profiles they can
 * already manage (same {@code accountAuthorization.canManage} rule as the
 * rest of the people screens). Pharmacists/staff themselves get read access
 * to their own documents.
 */
@RestController
@RequestMapping("/api/users/{userId}/documents")
public class ProfileDocumentController {

    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024; // 20 MB

    private final ProfileDocumentRepository documents;
    private final DocumentEncryptionService encryption;
    private final AppUserRepository users;
    private final UserProfileRepository profiles;
    private final AccountAuthorization authorization;

    public ProfileDocumentController(ProfileDocumentRepository documents,
                                     DocumentEncryptionService encryption,
                                     AppUserRepository users,
                                     UserProfileRepository profiles,
                                     AccountAuthorization authorization) {
        this.documents = documents;
        this.encryption = encryption;
        this.users = users;
        this.profiles = profiles;
        this.authorization = authorization;
    }

    // ---------- DTOs ----------

    public record DocumentDto(
        Long id,
        String fileName,
        String contentType,
        Long sizeBytes,
        String uploadedByName,
        String createdAt
    ) {}

    // ---------- Endpoints ----------

    @GetMapping
    @PreAuthorize("isAuthenticated() and @accountAuthorization.canViewDocuments(authentication, #userId)")
    public List<DocumentDto> list(@PathVariable Long userId) {
        return documents.findByUserIdOrderByIdDesc(userId).stream()
            .map(d -> toDto(d, id -> displayNameFor(id, null)))
            .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated() and @accountAuthorization.canManage(authentication, #userId)")
    public DocumentDto upload(@PathVariable Long userId,
                              Authentication auth,
                              @RequestParam("file") MultipartFile file) throws IOException {
        AppUser owner = users.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        AppUser uploader = currentUser(auth);

        if (file.isEmpty()) throw new IllegalArgumentException("File is empty");
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("File exceeds the 20 MB limit");
        }

        byte[] plaintext = file.getBytes();
        DocumentEncryptionService.Sealed sealed = encryption.encrypt(plaintext);

        ProfileDocument doc = new ProfileDocument();
        doc.setUser(owner);
        String name = file.getOriginalFilename();
        doc.setFileName(name == null || name.isBlank() ? "document" : name);
        String type = file.getContentType();
        doc.setContentType(type == null || type.isBlank() ? "application/octet-stream" : type);
        doc.setSizeBytes((long) plaintext.length);
        doc.setIv(sealed.iv());
        doc.setData(sealed.ciphertext());
        doc.setUploadedBy(uploader);

        ProfileDocument saved = documents.save(doc);
        return toDto(saved, id -> displayNameFor(id, uploader.getUsername()));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long userId,
                                           @PathVariable Long documentId,
                                           Authentication auth) {
        requireView(userId, auth);
        ProfileDocument doc = owned(userId, documentId);
        byte[] plaintext = encryption.decrypt(doc.getIv(), doc.getData());
        return ResponseEntity.ok()
            .header("Content-Disposition",
                "inline; filename=\"" + doc.getFileName().replace("\"", "") + "\"")
            .contentType(MediaType.parseMediaType(doc.getContentType()))
            .body(plaintext);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    @PreAuthorize("isAuthenticated() and @accountAuthorization.canManage(authentication, #userId)")
    public void delete(@PathVariable Long userId, @PathVariable Long documentId) {
        if (documents.deleteByIdAndUserId(documentId, userId) == 0) {
            throw new IllegalArgumentException("Document not found");
        }
    }

    // ---------- Helpers ----------

    private static DocumentDto toDto(ProfileDocument doc, java.util.function.Function<Long, String> nameResolver) {
        return new DocumentDto(
            doc.getId(),
            doc.getFileName(),
            doc.getContentType(),
            doc.getSizeBytes(),
            doc.getUploadedBy() != null
                ? nameResolver.apply(doc.getUploadedBy().getId())
                : null,
            doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null
        );
    }

    private static String displayName(AppUser user) {
        return user.getUsername(); // profile lookup happens via UserProfileRepository in caller
    }

    private String displayNameFor(Long userId, String fallback) {
        return profiles.findByUserId(userId)
            .map(p -> p.getFullName())
            .filter(n -> n != null && !n.isBlank())
            .orElse(fallback);
    }

    private AppUser currentUser(Authentication auth) {
        String username = auth != null ? auth.getName() : null;
        if (username == null) throw new IllegalArgumentException("Not authenticated");
        return users.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private ProfileDocument owned(Long userId, Long documentId) {
        return documents.findById(documentId)
            .filter(d -> d.getUser() != null && userId.equals(d.getUser().getId()))
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    /** Download and list share the canViewDocuments rule; enforce centrally here for download. */
    private void requireView(Long userId, Authentication auth) {
        if (!authorization.canViewDocuments(auth, userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed");
        }
    }
}
