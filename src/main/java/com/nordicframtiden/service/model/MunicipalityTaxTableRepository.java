package com.nordicframtiden.service.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MunicipalityTaxTableRepository extends JpaRepository<MunicipalityTaxTable, MunicipalityTaxTableId> {

  Optional<MunicipalityTaxTable> findByMunicipalityCodeAndTaxYear(String municipalityCode, Integer taxYear);
}
