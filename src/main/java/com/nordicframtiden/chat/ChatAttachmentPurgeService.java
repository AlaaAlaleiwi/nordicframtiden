package com.nordicframtiden.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Keeps attachment storage temporary: bytes are wiped as soon as every room
 * member has fetched them, with a scheduled safety net for orphans (uploads
 * never bound to a message) and attachments nobody picked up within the
 * retention window.
 */
@Service
public class ChatAttachmentPurgeService {

  private static final Logger log = LoggerFactory.getLogger(ChatAttachmentPurgeService.class);

  /** How long un-fetched attachment bytes are kept on the server. */
  static final long RETENTION_HOURS = 72;

  private final ChatAttachmentRepository attachments;
  private final ChatAttachmentDeliveryRepository deliveries;

  public ChatAttachmentPurgeService(ChatAttachmentRepository attachments,
                                    ChatAttachmentDeliveryRepository deliveries) {
    this.attachments = attachments;
    this.deliveries = deliveries;
  }

  /** Wipes the bytes when every room member has a delivery row. */
  @Transactional
  public void purgeIfFullyDelivered(Long attachmentId) {
    List<Long> ids = List.of(attachmentId);
    wipeAll(deliveries.findFullyDeliveredAttachmentIds().stream()
        .filter(ids::contains)
        .toList());
  }

  /** Wipes bytes of the given attachments, keeping metadata rows. */
  private void wipeAll(List<Long> attachmentIds) {
    if (attachmentIds.isEmpty()) return;
    Instant now = Instant.now();
    for (Long id : attachmentIds) {
      attachments.findById(id).ifPresent(attachment -> {
        attachment.setData(new byte[0]);
        attachment.setPurgedAt(now);
        attachments.save(attachment);
        deliveries.deleteByAttachmentId(id);
      });
    }
    if (!attachmentIds.isEmpty()) {
      log.debug("Purged {} chat attachment(s) from server storage", attachmentIds.size());
    }
  }

  /** Hourly sweep: fully delivered, orphaned and expired attachments. */
  @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 10 * 60 * 1000)
  @Transactional
  public void scheduledPurge() {
    Instant cutoff = Instant.now().minus(RETENTION_HOURS, ChronoUnit.HOURS);

    wipeAll(deliveries.findFullyDeliveredAttachmentIds());

    List<Long> orphans = deliveries.findOrphanedAttachmentIds(cutoff);
    if (!orphans.isEmpty()) {
      log.info("Purging {} orphaned chat upload(s)", orphans.size());
      wipeAll(orphans);
    }

    List<Long> expired = deliveries.findExpiredUndeliveredAttachmentIds(cutoff);
    if (!expired.isEmpty()) {
      log.info("Purging {} expired chat attachment(s) never fetched by all members", expired.size());
      wipeAll(expired);
    }
  }
}
