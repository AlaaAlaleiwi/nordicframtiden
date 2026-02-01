package com.nordicframtiden.availability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AvailabilityRequestRepository extends JpaRepository<AvailabilityRequest, Long> {

  List<AvailabilityRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

  @Query("select r from AvailabilityRequest r order by r.createdAt desc")
  List<AvailabilityRequest> findAllOrderByCreatedAtDesc();
}