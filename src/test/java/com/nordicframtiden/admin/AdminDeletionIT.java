package com.nordicframtiden.admin;

import com.nordicframtiden.chat.ChatAttachmentRepository;
import com.nordicframtiden.documents.ProfileDocument;
import com.nordicframtiden.documents.ProfileDocumentRepository;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression test for the production 500 on DELETE /api/admins/{id}: an admin
 * who uploaded profile documents to OTHER users' profiles (profile_document.
 * uploaded_by has no ON DELETE CASCADE) could never be deleted. All rows
 * referencing the account must be cleaned (or cascade) before the delete.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@WithMockUser(roles = "ADMIN")
@ActiveProfiles("test")
class AdminDeletionIT {

  @Autowired AdminService adminService;
  @Autowired AppUserRepository userRepo;
  @Autowired ProfileDocumentRepository profileDocumentRepo;
  @Autowired ChatAttachmentRepository chatAttachmentRepo;

  @Test
  void deleting_an_admin_who_uploaded_documents_to_others_succeeds() {
    String suffix = String.valueOf(System.nanoTime());

    // Admin A: the "test admin" that should be deletable.
    var uploader = adminService.createAdminWithProfile(
        true, "Deletable Admin", "del-admin-" + suffix + "@nordic.se",
        "071" + suffix.substring(suffix.length() - 7));

    // Admin B: keeps a document that A uploaded (B stays, A goes).
    var owner = adminService.createAdminWithProfile(
        true, "Document Owner", "doc-owner-" + suffix + "@nordic.se",
        "072" + suffix.substring(suffix.length() - 7));

    AppUser ownerUser = userRepo.findById(owner.id()).orElseThrow();
    AppUser uploaderUser = userRepo.findById(uploader.id()).orElseThrow();

    ProfileDocument doc = new ProfileDocument();
    doc.setUser(ownerUser);
    doc.setUploadedBy(uploaderUser);
    doc.setFileName("test-ids.pdf");
    doc.setContentType("application/pdf");
    doc.setSizeBytes(3L);
    doc.setIv(new byte[12]);
    doc.setData(new byte[] {1, 2, 3});
    profileDocumentRepo.save(doc);

    // Must not throw (before the fix: DataIntegrityViolationException / 500).
    assertThatCode(() -> adminService.deleteAdmin(uploader.id()))
        .doesNotThrowAnyException();

    assertThat(userRepo.findById(uploader.id())).isEmpty();
    // The document itself survives: its owner was not deleted.
    assertThat(profileDocumentRepo.findByUserIdOrderByIdDesc(owner.id()))
        .anyMatch(d -> "test-ids.pdf".equals(d.getFileName()));
  }
}
