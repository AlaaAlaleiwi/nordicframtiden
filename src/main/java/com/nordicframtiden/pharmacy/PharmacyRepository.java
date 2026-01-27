package com.nordicframtiden.pharmacy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {

  boolean existsByNameIgnoreCase(String name);

  @Query("select p from Pharmacy p order by p.id desc")
  List<Pharmacy> findAllNewestFirst();

  long countByEnabledTrue();
  long countByEnabledFalse();
}