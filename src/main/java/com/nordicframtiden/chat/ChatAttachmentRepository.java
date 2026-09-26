package com.nordicframtiden.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {

  List<ChatAttachment> findByMessageIdOrderById(Long messageId);

  List<ChatAttachment> findByIdInAndMessageIdIsNullAndUploaderId(List<Long> ids, Long uploaderId);
}
