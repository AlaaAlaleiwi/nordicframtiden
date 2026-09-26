package com.nordicframtiden.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;

/**
 * Records that a room member has fetched an attachment's bytes. Once every
 * member of the carrying message's room has a delivery row, the server can
 * purge the stored bytes (the metadata row stays for history display).
 */
@Entity
@Table(name = "chat_attachment_delivery")
@IdClass(ChatAttachmentDelivery.Id.class)
public class ChatAttachmentDelivery {

  @jakarta.persistence.Id
  @Column(name = "attachment_id", nullable = false)
  private Long attachmentId;

  @jakarta.persistence.Id
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "delivered_at", nullable = false)
  private Instant deliveredAt = Instant.now();

  public static class Id implements Serializable {
    private Long attachmentId;
    private Long userId;

    public Long getAttachmentId() { return attachmentId; }
    public void setAttachmentId(Long value) { this.attachmentId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
  }

  public Long getAttachmentId() { return attachmentId; }
  public void setAttachmentId(Long value) { this.attachmentId = value; }
  public Long getUserId() { return userId; }
  public void setUserId(Long value) { this.userId = value; }
  public Instant getDeliveredAt() { return deliveredAt; }
  public void setDeliveredAt(Instant value) { this.deliveredAt = value; }
}
