package com.nordicframtiden.availability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AvailabilityRequestRepository extends JpaRepository<AvailabilityRequest, Long> {

  List<AvailabilityRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

  @Query("select r from AvailabilityRequest r order by r.createdAt desc")
  List<AvailabilityRequest> findAllOrderByCreatedAtDesc();


    @Query("""
    select a from AvailabilityRequest a
    where a.status = com.nordicframtiden.availability.AvailabilityRequest.Status.APPROVED
      and a.startDate <= :endDate
      and a.endDate >= :startDate
  """)
  List<AvailabilityRequest> findApprovedOverlapping(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate
  );
}