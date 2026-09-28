package com.nordicframtiden.service;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import com.nordicframtiden.service.model.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PayslipFreezeService.class, SalaryAdjustmentService.class, PayslipRevisionIT.JsonConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
class PayslipRevisionIT {
  @Autowired PayslipFreezeService service;
  @Autowired SalaryAdjustmentService adjustments;
  @Autowired PayslipSnapshotRepository snapshots;
  @Autowired PayslipRevisionRepository revisions;
  @Autowired AppUserRepository users;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactionManager;
  TransactionTemplate transactions;
  @TestConfiguration static class JsonConfig {
    @Bean com.fasterxml.jackson.databind.ObjectMapper objectMapper() { return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(); }
  }
  @MockitoBean PayrollService payroll;
  Long userId;
  NetSalaryResponse original;
  @BeforeEach void setup() {
    transactions = new TransactionTemplate(transactionManager);
    AppUser user = new AppUser(); user.setUsername("payroll-test-" + UUID.randomUUID()); user.setPasswordHash("unused");
    userId = users.saveAndFlush(user).getId();
    original = new NetSalaryResponse(userId, "2026-08", new BigDecimal("200"), new BigDecimal("100"), new BigDecimal("20000"),
        2026, "0180", 30, 1, new BigDecimal("6000"), new BigDecimal("14000"));
    when(payroll.netSalaryForUserMonth(userId, 2026, 8)).thenReturn(original);
  }
  @AfterEach void cleanup() {
    transactions.executeWithoutResult(s -> snapshots.deleteByUserId(userId));
    users.deleteById(userId);
  }
  PayslipFreezeService.Correction change() {
    return new PayslipFreezeService.Correction(1, "Missed allowance", new BigDecimal("100"), BigDecimal.ZERO,
        new BigDecimal("30"), BigDecimal.ZERO, BigDecimal.ZERO);
  }
  @Test void migrationPreservesLegacyPayloadExactly() {
    jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
      String schema = "payroll_migration_" + UUID.randomUUID().toString().replace("-", "");
      try (var sql = connection.createStatement()) {
        sql.execute("create schema " + schema);
        try {
          sql.execute("set search_path to " + schema);
          for (String file : List.of("V30__create_payslip_snapshot.sql", "V44__immutable_payslip_revisions.sql")) {
            try (var resource = getClass().getResourceAsStream("/db/migration/" + file)) {
              sql.execute(new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
            if (file.startsWith("V30")) sql.execute("insert into payslip_snapshot (user_id, year, month, role, payload) values (7, 2026, 8, 'USER', '{  \"legacy\": true  }')");
          }
          try (var rows = sql.executeQuery("select s.payload = r.payload as same, r.actor, r.revision from payslip_snapshot s join payslip_revision r on r.snapshot_id = s.id")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean("same")).isTrue();
            assertThat(rows.getString("actor")).isEqualTo("legacy-import");
            assertThat(rows.getInt("revision")).isEqualTo(1);
          }
        } finally {
          sql.execute("set search_path to public");
          sql.execute("drop schema " + schema + " cascade");
        }
      }
      return null;
    });
  }
  @Test void databaseRejectsRewritesAndAccountDeletionCascades() {
    service.finalizePayslip(userId, 2026, 8, "USER", "first");
    var snapshot = snapshots.findByUserIdAndYearAndMonthAndRole(userId, 2026, 8, "USER").orElseThrow();
    service.correct(userId, 2026, 8, "USER", change(), "second");
    assertThat(service.history(userId, 2026, 8, "USER")).hasSize(2);
    assertThatThrownBy(() -> jdbc.update("update payslip_snapshot set payload = '{}' where id = ?", snapshot.getId()))
        .hasMessageContaining("immutable");
    assertThatThrownBy(() -> jdbc.update("update payslip_revision set payload = '{}' where snapshot_id = ?", snapshot.getId()))
        .hasMessageContaining("immutable");
    assertThat(snapshots.findById(snapshot.getId()).orElseThrow().getPayload()).isEqualTo(snapshot.getPayload());
    transactions.executeWithoutResult(s -> {
      assertThat(snapshots.findByUserId(userId).getFirst().getRevisions()).hasSize(2); // GDPR export relationship
      snapshots.deleteByUserId(userId);
    });
    assertThat(revisions.findBySnapshotIdOrderByRevisionAsc(snapshot.getId())).isEmpty();
  }
  @Test void oneTimeCorrectionsAffectFutureDraftEstimatesWithoutMutatingOriginalAdjustments() {
    service.finalizePayslip(userId, 2026, 8, "USER", "first");
    service.correct(userId, 2026, 8, "USER", new PayslipFreezeService.Correction(1, "Missed bonus",
        BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("30"), BigDecimal.ZERO), "second");
    assertThat(adjustments.annualOneTimeTotal(userId, 2026)).isEqualByComparingTo("100");
    assertThat(adjustments.annualOneTimeTotalExcludingMonth(userId, 2026, 9)).isEqualByComparingTo("100");
    assertThat(adjustments.annualOneTimeTotalExcludingMonth(userId, 2026, 8)).isZero();
    assertThat(adjustments.annualOneTimeTotal(userId, 2027)).isZero();
    assertThat(adjustments.forMonth(userId, 2026, 8)).isEmpty();
    service.correct(userId, 2026, 8, "USER", new PayslipFreezeService.Correction(2, "Reverse bonus",
        BigDecimal.ZERO, new BigDecimal("-100"), BigDecimal.ZERO, new BigDecimal("-30"), BigDecimal.ZERO), "third");
    assertThat(adjustments.annualOneTimeTotal(userId, 2026)).isZero();
  }
  @Test void concurrentCorrectionsHaveExactlyOneWinner() throws Exception {
    service.finalizePayslip(userId, 2026, 8, "USER", "first");
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<String> action = () -> {
        start.await();
        try { service.correct(userId, 2026, 8, "USER", change(), "operator"); return "saved"; }
        catch (PayslipConflictException expected) { return "conflict"; }
      };
      var a = executor.submit(action); var b = executor.submit(action); start.countDown();
      assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder("saved", "conflict");
    }
    assertThat(service.history(userId, 2026, 8, "USER")).hasSize(2);
    assertThat(service.resolve(userId, 2026, 8, "USER").netSalary()).isEqualByComparingTo("14070");
    verify(payroll, times(1)).netSalaryForUserMonth(userId, 2026, 8);
  }
  @Test void concurrentFinalizationProducesOneOriginal() throws Exception {
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<Integer> action = () -> { start.await(); return service.finalizePayslip(userId, 2026, 8, "USER", "operator").revision(); };
      var a = executor.submit(action); var b = executor.submit(action); start.countDown();
      assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(1);
      assertThat(b.get(10, TimeUnit.SECONDS)).isEqualTo(1);
    }
    assertThat(service.history(userId, 2026, 8, "USER")).hasSize(1);
    verify(payroll, times(1)).netSalaryForUserMonth(userId, 2026, 8);
  }
}
