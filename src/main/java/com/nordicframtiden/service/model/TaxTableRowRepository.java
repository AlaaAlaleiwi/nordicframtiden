package com.nordicframtiden.service.model;

import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaxTableRowRepository extends JpaRepository<TaxTableRow, Long> {
 @Cacheable(
        cacheNames = "taxTableRows",
        key = "#year + '-' + #table + '-' + #salary"
    )
  @Query("""
    SELECT r FROM TaxTableRow r
    WHERE r.taxYear = :year
      AND r.tableNumber = :table
      AND :salary BETWEEN r.incomeFrom AND r.incomeTo
  """)
  Optional<TaxTableRow> findRow(int year, int table, int salary);
}


