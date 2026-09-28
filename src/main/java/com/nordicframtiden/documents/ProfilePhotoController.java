package com.nordicframtiden.documents;

import com.nordicframtiden.security.AccountAuthorization;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Profile photo endpoints. Upload/manage follow the same canManage rule as the
 * rest of the people screens (admins, PERM_PEOPLE on pharmacists, and the
 * account owner via the /me variant); reading is authenticated app-wide.
 */
@RestController
@RequestMapping("/api/users/{userId}/photo")
public class ProfilePhotoController {

    private final ProfilePhotoService photos;
    private final AccountAuthorization authorization;

    public ProfilePhotoController(ProfilePhotoService photos, AccountAuthorization authorization) {
        this.photos = photos;
        this.authorization = authorization;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated() and @accountAuthorization.canManage(authentication, #userId)")
    public ProfilePhotoService.PhotoDto upload(@PathVariable Long userId,
                                               @RequestParam("file") MultipartFile file) throws IOException {
        return photos.upload(userId, file);
    }

    @GetMapping
    public ResponseEntity<byte[]> get(@PathVariable Long userId,
                                      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        ProfilePhotoService.PhotoData photo = photos.load(userId, ifNoneMatch);

        String etag = "\"" + photo.version() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(etag)
                .build();
        }

        return ResponseEntity.ok()
            .eTag(etag)
            .contentType(MediaType.parseMediaType(photo.contentType()))
            .body(photo.bytes());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated() and @accountAuthorization.canManage(authentication, #userId)")
    public void delete(@PathVariable Long userId) {
        photos.clear(userId);
    }
}
