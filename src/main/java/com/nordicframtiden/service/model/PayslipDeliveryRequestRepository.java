package com.nordicframtiden.service.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PayslipDeliveryRequestRepository
    extends JpaRepository<PayslipDeliveryRequest, Long> {

  /** Queue for the monthly job, oldest first. */
  List<PayslipDeliveryRequest> findByStatusOrderByCreatedAtAsc(String status);

  /** Exists already for this user/work month/role — do not queue twice. */
  Optional<PayslipDeliveryRequest> findByUserIdAndWorkYearAndWorkMonthAndRole(
      Long userId, int workYear, int workMonth, String role);

  /** Latest finished delivery for the "ready" status endpoint. */
  Optional<PayslipDeliveryRequest> findFirstByUserIdAndRoleAndStatusOrderByWorkYearDescWorkMonthDescSentAtDesc(
      Long userId, String role, String status);

  /**
   * Exactly-once claim: flips a single PENDING row to SENDING and reports
   * whether this worker won it. Rows claimed by another worker return 0, so
   * overlapping job runs can never mail the same payslip twice.
   */
  @Modifying
  @Transactional
  @Query("""
      update PayslipDeliveryRequest r
      set r.status = 'SENDING', r.claimedAt = :now
      where r.id = :id and r.status = 'PENDING'
      """)
  int claimIfPending(@Param("id") Long id, @Param("now") Instant now);

  /** Terminal SENT write; only a SENDING claim can be completed. */
  @Modifying
  @Transactional
  @Query("""
      update PayslipDeliveryRequest r
      set r.status = 'SENT', r.sentAt = :sentAt, r.lastError = null
      where r.id = :id and r.status = 'SENDING'
      """)
  int markSentIfSending(@Param("id") Long id, @Param("sentAt") Instant sentAt);

  /**
   * Releases a claim without penalty (e.g. mail disabled/unconfigured): back
   * to PENDING so the next daily run retries, attempts unchanged.
   */
  @Modifying
  @Transactional
  @Query("""
      update PayslipDeliveryRequest r
      set r.status = 'PENDING', r.claimedAt = null
      where r.id = :id and r.status = 'SENDING'
      """)
  int releaseClaimIfSending(@Param("id") Long id);

  /**
   * Records a failed attempt against a held claim: the error, the new attempt
   * count, and either PENDING (retry tomorrow) or FAILED (give up for good).
   */
  @Modifying
  @Transactional
  @Query("""
      update PayslipDeliveryRequest r
      set r.status = :nextStatus, r.claimedAt = null, r.attempts = :attempts,
          r.lastError = :message
      where r.id = :id and r.status = 'SENDING'
      """)
  int recordFailureIfSending(@Param("id") Long id, @Param("message") String message,
      @Param("nextStatus") String nextStatus, @Param("attempts") int attempts);

  /**
   * Recovers rows stuck in SENDING (worker crashed mid-send and the outcome is
   * unknown): after the grace period they return to PENDING so the next run
   * retries them — still bounded by the attempts cap, and the claim ensures
   * only one worker touches a row at any moment.
   */
  @Modifying
  @Transactional
  @Query("""
      update PayslipDeliveryRequest r
      set r.status = 'PENDING', r.claimedAt = null, r.attempts = r.attempts + 1,
          r.lastError = 'stale SENDING claim recovered'
      where r.status = 'SENDING' and r.claimedAt < :staleBefore
      """)
  int releaseStaleClaims(@Param("staleBefore") Instant staleBefore);

  void deleteByUserId(Long userId);
}
