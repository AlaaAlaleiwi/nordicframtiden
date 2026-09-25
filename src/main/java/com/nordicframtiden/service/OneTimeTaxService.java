package com.nordicframtiden.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OneTimeTaxService {
  private final JdbcTemplate jdbcTemplate;
  public OneTimeTaxService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

  public int rateFor(int year, int column, int annualIncome) {
    return jdbcTemplate.query("""
        SELECT tax_percent FROM one_time_tax_rate
        WHERE tax_year = ? AND tax_column = ? AND ? BETWEEN annual_income_from AND annual_income_to
        """, result -> {
          if (!result.next()) throw new IllegalArgumentException("No one-time tax rate for year " + year + ", column " + column + " and annual income " + annualIncome);
          return result.getInt(1);
        }, year, column, annualIncome);
  }
}
