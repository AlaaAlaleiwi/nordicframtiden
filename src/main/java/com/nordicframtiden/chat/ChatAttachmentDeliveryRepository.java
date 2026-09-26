package com.nordicframtiden.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ChatAttachmentDeliveryRepository extends JpaRepository<ChatAttachmentDelivery, ChatAttachmentDelivery.Id> {

  long countByAttachmentId(Long attachmentId);

  boolean existsByAttachmentIdAndUserId(Long attachmentId, Long userId);

  @Modifying
  void deleteByAttachmentId(Long attachmentId);

  /** Distinct attachment ids that carry a message and were delivered to everyone. */
  @Query("""
      select a.id from ChatAttachment a
      where a.messageId is not null
        and a.purgedAt is null
        and (select count(d) from ChatAttachmentDelivery d where d.attachmentId = a.id)
            >= (select count(m.user.id) from ChatRoomMember m where m.room.id =
                  (select msg.room.id from ChatMessage msg where msg.id = a.messageId))
      """)
  List<Long> findFullyDeliveredAttachmentIds();

  /** Orphaned uploads: never bound to a message and older than the cutoff. */
  @Query("""
      select a.id from ChatAttachment a
      where a.messageId is null
        and a.purgedAt is null
        and a.createdAt < :cutoff
      """)
  List<Long> findOrphanedAttachmentIds(@Param("cutoff") Instant cutoff);

  /** Attachments still undelivered to everyone after the retention window. */
  @Query("""
      select a.id from ChatAttachment a
      where a.messageId is not null
        and a.purgedAt is null
        and a.createdAt < :cutoff
      """)
  List<Long> findExpiredUndeliveredAttachmentIds(@Param("cutoff") Instant cutoff);
}
