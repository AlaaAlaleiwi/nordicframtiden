package com.nordicframtiden.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileDocumentRepository extends JpaRepository<ProfileDocument, Long> {

    List<ProfileDocument> findByUserIdOrderByIdDesc(Long userId);

    long deleteByIdAndUserId(Long id, Long userId);
}
