package com.dehui.property.modules.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.modules.parking.controller.ParkingController;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "PARKING_DB_INTEGRATION", matches = "true")
class ParkingBillingV36MySqlIntegrationTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private Long assignmentId;
    private Long tenantId;

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

        Long parkingSpaceId = jdbcTemplate.queryForObject(
                """
                SELECT space_record.id
                FROM parking_space space_record
                WHERE space_record.deleted = 0
                  AND space_record.status IN ('ACTIVE', 'ENABLED')
                  AND NOT EXISTS (
                      SELECT 1
                      FROM parking_assignment assignment_record
                      WHERE assignment_record.parking_space_id = space_record.id
                        AND assignment_record.deleted = 0
                        AND assignment_record.status IN ('ACTIVE', 'ENABLED')
                  )
                ORDER BY space_record.id
                LIMIT 1
                """,
                Long.class
        );
        tenantId = jdbcTemplate.queryForObject(
                "SELECT id FROM tenant WHERE deleted = 0 AND status IN ('ACTIVE', 'ENABLED') ORDER BY id LIMIT 1",
                Long.class
        );
        String code = "TEST-V36-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbcTemplate.update(
                """
                INSERT INTO parking_assignment
                    (code, parking_space_id, tenant_id, car_plate_no, start_date, monthly_fee,
                     status, created_by, updated_by, deleted, remark)
                VALUES (?, ?, ?, 'TEST-V36', '2026-01-01', 123.45,
                        'ACTIVE', 0, 0, 0, 'LOCAL V36 INTEGRATION TEST')
                """,
                code,
                parkingSpaceId,
                tenantId
        );
        assignmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM parking_assignment WHERE code = ? AND deleted = 0",
                Long.class,
                code
        );
    }

    @AfterEach
    void tearDown() {
        if (jdbcTemplate == null || assignmentId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM parking_bill WHERE assignment_id = ?", assignmentId);
        jdbcTemplate.update("DELETE FROM parking_assignment WHERE id = ?", assignmentId);
    }

    @Test
    void concurrentSyncCreatesOneActiveBillAndSkipsTheOther() throws Exception {
        CyclicBarrier queryBarrier = new CyclicBarrier(2);
        ParkingController first = new ParkingController(new BarrierJdbcTemplate(dataSource, queryBarrier));
        ParkingController second = new ParkingController(new BarrierJdbcTemplate(dataSource, queryBarrier));
        TransactionTemplate firstTransaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        TransactionTemplate secondTransaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ApiResponse<Map<String, Object>>> firstResult = executor.submit(
                    () -> firstTransaction.execute(status -> first.syncBills(Map.of("month", "2099-01")))
            );
            Future<ApiResponse<Map<String, Object>>> secondResult = executor.submit(
                    () -> secondTransaction.execute(status -> second.syncBills(Map.of("month", "2099-01")))
            );

            Map<String, Object> firstData = firstResult.get(15, TimeUnit.SECONDS).data();
            Map<String, Object> secondData = secondResult.get(15, TimeUnit.SECONDS).data();
            int successes = number(firstData.get("successCount")) + number(secondData.get("successCount"));
            int skipped = number(firstData.get("skippedCount")) + number(secondData.get("skippedCount"));

            assertEquals(1, successes);
            assertTrue(skipped >= 1);
            assertEquals(1, jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM parking_bill
                    WHERE assignment_id = ? AND billing_month = '2099-01'
                      AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED')
                    """,
                    Integer.class,
                    assignmentId
            ));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void uniqueKeyKeepsLegacyNullsAndReleasesVoidedOrDeletedRows() {
        insertBill("NULL-A", null, "UNPAID", "ACTIVE", 0);
        insertBill("NULL-B", null, "UNPAID", "ACTIVE", 0);

        insertBill("PAID-A", "2099-02", "PAID", "ACTIVE", 0);
        assertThrows(
                DuplicateKeyException.class,
                () -> insertBill("PAID-B", "2099-02", "UNPAID", "ACTIVE", 0)
        );

        jdbcTemplate.update(
                "UPDATE parking_bill SET status = 'VOID' WHERE assignment_id = ? AND billing_month = '2099-02'",
                assignmentId
        );
        insertBill("AFTER-VOID", "2099-02", "UNPAID", "ACTIVE", 0);

        jdbcTemplate.update(
                "UPDATE parking_bill SET deleted = 1 WHERE assignment_id = ? AND billing_month = '2099-02' AND status = 'ACTIVE'",
                assignmentId
        );
        insertBill("AFTER-DELETE", "2099-02", "UNPAID", "ACTIVE", 0);

        assertEquals(1, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM parking_bill
                WHERE assignment_id = ? AND billing_month = '2099-02'
                  AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED')
                """,
                Integer.class,
                assignmentId
        ));
    }

    @Test
    void migrationAppliesToAnEmptyTableAndAllowsLegacyNullHistory() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            createTemporaryParkingBillTable(statement);
            statement.executeUpdate("""
                    INSERT INTO parking_bill (assignment_id, billing_month, deleted, status)
                    VALUES (1, NULL, 0, 'ACTIVE'), (1, NULL, 0, 'ACTIVE')
                    """);

            statement.execute(readV36Migration());
            statement.executeUpdate("""
                    INSERT INTO parking_bill (assignment_id, billing_month, deleted, status)
                    VALUES (1, '2099-03', 0, 'ACTIVE')
                    """);

            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO parking_bill (assignment_id, billing_month, deleted, status)
                    VALUES (1, '2099-03', 0, 'ACTIVE')
                    """));
        }
    }

    @Test
    void migrationRejectsPreexistingEffectiveMonthDuplicates() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            createTemporaryParkingBillTable(statement);
            statement.executeUpdate("""
                    INSERT INTO parking_bill (assignment_id, billing_month, deleted, status)
                    VALUES (1, '2099-04', 0, 'ACTIVE'), (1, '2099-04', 0, 'ACTIVE')
                    """);

            assertThrows(SQLException.class, () -> statement.execute(readV36Migration()));
        }
    }

    private void insertBill(String suffix, String billingMonth, String payStatus, String status, int deleted) {
        jdbcTemplate.update(
                """
                INSERT INTO parking_bill
                    (code, assignment_id, tenant_id, amount, billing_month, due_date,
                     pay_status, status, created_by, updated_by, deleted, remark)
                VALUES (?, ?, ?, 123.45, ?, '2099-02-28', ?, ?, 0, 0, ?, 'LOCAL V36 INTEGRATION TEST')
                """,
                "TEST-V36-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                assignmentId,
                tenantId,
                billingMonth,
                payStatus,
                status,
                deleted
        );
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static void createTemporaryParkingBillTable(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TEMPORARY TABLE parking_bill (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    assignment_id BIGINT NOT NULL,
                    billing_month CHAR(7) NULL,
                    deleted TINYINT(1) NOT NULL DEFAULT 0,
                    status VARCHAR(32) NOT NULL
                ) ENGINE=InnoDB
                """);
    }

    private static String readV36Migration() throws Exception {
        try (InputStream input = ParkingBillingV36MySqlIntegrationTest.class.getResourceAsStream(
                "/db/migration/V36__enforce_parking_bill_active_month_uniqueness.sql")) {
            if (input == null) {
                throw new IllegalStateException("V36 migration resource is missing");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the local MySQL integration test");
        }
        return value;
    }

    private static void requireDevelopmentJdbcUrl(String jdbcUrl) {
        String databasePath = jdbcUrl.replaceFirst("^jdbc:mysql://[^/]+/", "");
        String databaseName = databasePath.split("\\?", 2)[0];
        if (!"dehui_property_dev".equals(databaseName)) {
            throw new IllegalStateException("Refusing non-development MySQL database: " + databaseName);
        }
    }

    private static void assertDevelopmentDatabase(DriverManagerDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            assertTrue(result.next());
            assertEquals("dehui_property_dev", result.getString(1));
        }
    }

    private static final class BarrierJdbcTemplate extends JdbcTemplate {

        private final CyclicBarrier queryBarrier;

        private BarrierJdbcTemplate(DriverManagerDataSource dataSource, CyclicBarrier queryBarrier) {
            super(dataSource);
            this.queryBarrier = queryBarrier;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            List<Map<String, Object>> rows = super.queryForList(sql, args);
            if (sql.contains("FROM parking_assignment pa") && sql.contains("hasActiveBill")) {
                try {
                    queryBarrier.await(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("Concurrent query barrier failed", exception);
                }
            }
            return rows;
        }
    }
}
