package com.nordicframtiden.service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxTableImportStore {
  private static final int MINIMUM_COMPLETE_ROW_COUNT = 7_000;

  private final JdbcTemplate jdbcTemplate;
  // Optional: profiles with spring.cache.type=none (e.g. tests) expose no
  // CacheManager bean. Caching is only an optimization here — the manager
  // is used solely to evict "taxTableRows" after an import.
  private final CacheManager cacheManager;

  public TaxTableImportStore(JdbcTemplate jdbcTemplate, ObjectProvider<CacheManager> cacheManager) {
    this.jdbcTemplate = jdbcTemplate;
    this.cacheManager = cacheManager.getIfAvailable();
  }

  public boolean hasCompleteYear(int year) {
    Long rowCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tax_table_row WHERE tax_year = ?", Long.class, year);
    Long percentageRowCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM tax_table_row WHERE tax_year = ? AND percentage", Long.class, year);
    return rowCount != null && rowCount >= MINIMUM_COMPLETE_ROW_COUNT
        && percentageRowCount != null && percentageRowCount > 0;
  }

  public boolean hasCompleteOneTimeYear(int year) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) >= 24
          AND COUNT(DISTINCT tax_column) = 6
          AND COUNT(*) FILTER (WHERE annual_income_to = 2147483647) = 6
        FROM one_time_tax_rate WHERE tax_year = ?
        """, Boolean.class, year));
  }

  @Transactional
  public void replaceOneTimeYear(int year, List<SkatteverketTaxTableImporter.OneTimeTaxImportRow> rows) {
    jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + (7_350_000L + year) + ")");
    jdbcTemplate.update("DELETE FROM one_time_tax_rate WHERE tax_year = ?", year);
    jdbcTemplate.batchUpdate("INSERT INTO one_time_tax_rate (tax_year,tax_column,annual_income_from,annual_income_to,tax_percent) VALUES (?,?,?,?,?)", rows, 100, (statement,row) -> {
      statement.setInt(1,row.taxYear()); statement.setInt(2,row.taxColumn()); statement.setInt(3,row.annualIncomeFrom()); statement.setInt(4,row.annualIncomeTo()); statement.setInt(5,row.taxPercent());
    });
  }

  @Transactional
  public void replaceYear(int year, List<SkatteverketTaxTableImporter.TaxTableImportRow> rows) {
    // Only one Cloud Run instance may replace a given year at a time.
    jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + (7_340_000L + year) + ")");
    jdbcTemplate.update("DELETE FROM tax_table_row WHERE tax_year = ?", year);
    jdbcTemplate.batchUpdate("""
        INSERT INTO tax_table_row
          (tax_year, table_number, income_from, income_to,
           col_1, col_2, col_3, col_4, col_5, col_6, percentage)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, rows, 500, this::setParameters);
    var cache = cacheManager != null ? cacheManager.getCache("taxTableRows") : null;
    if (cache != null) {
      cache.clear();
    }
  }

  private void setParameters(PreparedStatement statement,
      SkatteverketTaxTableImporter.TaxTableImportRow row) throws SQLException {
    statement.setInt(1, row.taxYear());
    statement.setInt(2, row.tableNumber());
    statement.setInt(3, row.incomeFrom());
    statement.setInt(4, row.incomeTo());
    statement.setInt(5, row.col1());
    statement.setInt(6, row.col2());
    statement.setInt(7, row.col3());
    statement.setInt(8, row.col4());
    statement.setInt(9, row.col5());
    statement.setInt(10, row.col6());
    statement.setBoolean(11, row.percentage());
  }
}
