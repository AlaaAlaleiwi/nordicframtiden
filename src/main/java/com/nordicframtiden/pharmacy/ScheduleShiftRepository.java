package com.nordicframtiden.pharmacy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public interface ScheduleShiftRepository extends JpaRepository<ScheduleShift, Long> {

    

  @Query("""
    select s from ScheduleShift s
    where s.startAt < :end
      and s.endAt > :start
      and (:pharmacyId is null or s.pharmacy.id = :pharmacyId)
      and (:userId is null or s.user.id = :userId)
    order by s.startAt asc
  """)
  List<ScheduleShift> findInRange(
      @Param("start") OffsetDateTime start,
      @Param("end") OffsetDateTime end,
      @Param("pharmacyId") Long pharmacyId,
      @Param("userId") Long userId
  );
}