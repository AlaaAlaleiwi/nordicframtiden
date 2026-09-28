package com.nordicframtiden.gdpr;

import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Executes approved GDPR deletion requests whose scheduled date has arrived.
 * Runs shortly after the export job so a same-night export+deletion sequence
 * stays ordered (export first, deletion second).
 */
@Service
public class GdprDeletionJob {

  private static final Logger log = LoggerFactory.getLogger(GdprDeletionJob.class);

  static final String CRON = "0 30 3 * * *";
  static final String ZONE = GdprExportMailJob.ZONE;

  private final GdprDeletionService deletionService;

  public GdprDeletionJob(GdprDeletionService deletionService) {
    this.deletionService = deletionService;
  }

  @Scheduled(cron = GdprDeletionJob.CRON, zone = GdprDeletionJob.ZONE)
  public void executeDueDeletions() {
    LocalDate today = LocalDate.now(ZoneId.of(GdprDeletionJob.ZONE));
    int executed = deletionService.executeDue(today);
    if (executed > 0) {
      log.info("GDPR deletion job: executed {} scheduled deletion(s)", executed);
    }
  }
}
