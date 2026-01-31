package com.nordicframtiden.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface StaffShiftRepository extends JpaRepository<StaffShift, Long> {

  @Query("""
    select s from StaffShift s
    where s.startAt < :end
      and s.endAt > :start
      and (:userId is null or s.user.id = :userId)
    order by s.startAt asc
  """)
  List<StaffShift> findInRange(
      @Param("start") OffsetDateTime start,
      @Param("end") OffsetDateTime end,
      @Param("userId") Long userId
  );
}