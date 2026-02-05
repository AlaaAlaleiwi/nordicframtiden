package com.nordicframtiden.contact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ContactRequestRepository extends JpaRepository<ContactRequest, Long> {

  @Query("select c from ContactRequest c order by c.createdAt desc")
  List<ContactRequest> findAllNewestFirst();

  long countByHandledFalse();
}