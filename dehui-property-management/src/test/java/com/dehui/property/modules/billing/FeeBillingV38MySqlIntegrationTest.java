package com.dehui.property.modules.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Opt-in destructive checks, restricted to the local dehui_property_dev database. */
@EnabledIfEnvironmentVariable(named = "FEE_BILLING_DB_INTEGRATION", matches = "true")
class FeeBillingV38MySqlIntegrationTest {

    private static final long OPERATOR_ID = 72_002L;

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private Long feeRuleId;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = requiredEnvironment("MYSQL_URL");
        requireDevelopmentJdbcUrl(jdbcUrl);
        dataSource = new DriverManagerDataSource(
                jdbcUrl,
                requiredEnvironment("MYSQL_USERNAME"),
                requiredEnvironment("MYSQL_PASSWORD")
        );
        assertDevelopmentDatabase(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
        createFeeRule();
    }

    @AfterEach
    void tearDown() {
        if (jdbcTemplate == null || feeRuleId == null) return;
        jdbcTemplate.update("DELETE FROM fee_rule_version WHERE fee_rule_id = ?", feeRuleId);
        jdbcTemplate.update("DELETE FROM fee_rule WHERE id = ?", feeRuleId);
    }

    @Test
    void databaseAllowsOnlyOneActiveVersionPerFeeRule() {
        Long first = insertVersion(1, "ACTIVE");
        Long second = insertVersion(2, "DRAFT");
        jdbcTemplate.update(
                "UPDATE fee_rule SET current_version_id = ?, current_version_no = 2, active_version_id = ?, active_version_no = 1 WHERE id = ?",
                second, first, feeRuleId
        );

        assertThrows(DuplicateKeyException.class,
                () -> jdbcTemplate.update("UPDATE fee_rule_version SET status = 'ACTIVE' WHERE id = ?", second));
        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "SELECT status FROM fee_rule_version WHERE id = ?", String.class, second
        ));
    }

    @Test
    void concurrentOptimisticUpdatesCommitOnlyOneAggregateVersion() throws Exception {
        Long first = insertVersion(1, "DRAFT");
        jdbcTemplate.update("UPDATE fee_rule SET current_version_id = ? WHERE id = ?", first, feeRuleId);
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> one = executor.submit(() -> updateAfterBarrier(start, "concurrent-one"));
            Future<Integer> two = executor.submit(() -> updateAfterBarrier(start, "concurrent-two"));
            assertEquals(1, one.get(10, TimeUnit.SECONDS) + two.get(10, TimeUnit.SECONDS));
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT version FROM fee_rule WHERE id = ?", Long.class, feeRuleId
            ));
        } finally {
            executor.shutdownNow();
        }
    }

    private int updateAfterBarrier(CyclicBarrier start, String description) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return jdbcTemplate.update(
                "UPDATE fee_rule SET description = ?, version = version + 1 WHERE id = ? AND version = 0",
                description, feeRuleId
        );
    }

    private void createFeeRule() {
        String code = "TEST-V38-FR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        jdbcTemplate.update(
                "INSERT INTO fee_rule (rule_code, rule_name, fee_type, status, current_version_no, version, created_by, updated_by) "
                        + "VALUES (?, 'V38 integration rule', 'PROPERTY', 'DRAFT', 1, 0, ?, ?)",
                code, OPERATOR_ID, OPERATOR_ID
        );
        feeRuleId = jdbcTemplate.queryForObject("SELECT id FROM fee_rule WHERE rule_code = ?", Long.class, code);
    }

    private Long insertVersion(int versionNo, String status) {
        jdbcTemplate.update(
                "INSERT INTO fee_rule_version (fee_rule_id, version_no, calculation_type, billing_cycle, effective_from, "
                        + "fixed_amount, currency, status, activated_at, activated_by, created_by) "
                        + "VALUES (?, ?, 'FIXED', 'MONTHLY', '2026-01-01', 100, 'CNY', ?, "
                        + "CASE WHEN ? = 'ACTIVE' THEN NOW() ELSE NULL END, "
                        + "CASE WHEN ? = 'ACTIVE' THEN ? ELSE NULL END, ?)",
                feeRuleId, versionNo, status, status, status, OPERATOR_ID, OPERATOR_ID
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM fee_rule_version WHERE fee_rule_id = ? AND version_no = ?",
                Long.class, feeRuleId, versionNo
        );
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    private static void requireDevelopmentJdbcUrl(String jdbcUrl) {
        String databaseName = jdbcUrl.replaceFirst("^jdbc:mysql://[^/]+/", "").split("\\?", 2)[0];
        if (!"dehui_property_dev".equals(databaseName)) {
            throw new IllegalStateException("Refusing non-development MySQL database: " + databaseName);
        }
    }

    private static void assertDevelopmentDatabase(DriverManagerDataSource source) throws Exception {
        try (Connection connection = source.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT DATABASE()")) {
            assertTrue(resultSet.next());
            assertEquals("dehui_property_dev", resultSet.getString(1));
        }
    }
}
