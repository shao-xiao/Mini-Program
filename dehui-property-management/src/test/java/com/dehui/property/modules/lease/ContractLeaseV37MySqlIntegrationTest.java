package com.dehui.property.modules.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dehui.property.modules.contract.infrastructure.repository.ContractRepository;
import com.dehui.property.modules.lease.application.LeaseApplicationService;
import com.dehui.property.modules.lease.dto.LeaseActionRequest;
import com.dehui.property.modules.lease.infrastructure.repository.LeaseRepository;
import com.dehui.property.modules.shared.infrastructure.DomainEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Destructive local integration coverage for the V37 room-occupancy invariant.
 * It is deliberately opt-in and refuses every database except dehui_property_dev.
 */
@EnabledIfEnvironmentVariable(named = "LEASE_DB_INTEGRATION", matches = "true")
class ContractLeaseV37MySqlIntegrationTest {

    private static final long OPERATOR_ID = 71_001L;

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private LeaseApplicationService leaseService;
    private TransactionTemplate transactionTemplate;
    private final List<Long> leaseIds = new ArrayList<>();
    private final List<Long> contractIds = new ArrayList<>();
    private Long tenantId;
    private Long buildingId;
    private Long floorId;
    private Long roomId;

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
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        leaseService = new LeaseApplicationService(
                new LeaseRepository(jdbcTemplate),
                new ContractRepository(jdbcTemplate),
                new DomainEventRepository(jdbcTemplate),
                new ObjectMapper().findAndRegisterModules()
        );
        createFixture();
    }

    @AfterEach
    void tearDown() {
        if (jdbcTemplate == null) return;
        for (Long leaseId : leaseIds) {
            jdbcTemplate.update("DELETE FROM outbox_event WHERE aggregate_type = 'Lease' AND aggregate_id = ?", leaseId);
            jdbcTemplate.update("DELETE FROM domain_audit_log WHERE aggregate_type = 'Lease' AND aggregate_id = ?", leaseId);
            jdbcTemplate.update("DELETE FROM lease_event WHERE lease_id = ?", leaseId);
            jdbcTemplate.update("DELETE FROM lease_status_history WHERE lease_id = ?", leaseId);
            jdbcTemplate.update("DELETE FROM lease_room WHERE lease_id = ?", leaseId);
            jdbcTemplate.update("DELETE FROM lease WHERE id = ?", leaseId);
        }
        for (Long contractId : contractIds) {
            jdbcTemplate.update("DELETE FROM outbox_event WHERE aggregate_type = 'Contract' AND aggregate_id = ?", contractId);
            jdbcTemplate.update("DELETE FROM domain_audit_log WHERE aggregate_type = 'Contract' AND aggregate_id = ?", contractId);
            jdbcTemplate.update("DELETE FROM contract_status_history WHERE contract_id = ?", contractId);
            jdbcTemplate.update("DELETE FROM contract_revision WHERE contract_id = ?", contractId);
            jdbcTemplate.update("DELETE FROM contract_room WHERE contract_id = ?", contractId);
            jdbcTemplate.update("DELETE FROM contract WHERE id = ?", contractId);
        }
        if (roomId != null) jdbcTemplate.update("DELETE FROM building_room WHERE id = ?", roomId);
        if (floorId != null) jdbcTemplate.update("DELETE FROM building_floor WHERE id = ?", floorId);
        if (buildingId != null) jdbcTemplate.update("DELETE FROM building WHERE id = ?", buildingId);
        if (tenantId != null) jdbcTemplate.update("DELETE FROM tenant WHERE id = ?", tenantId);
    }

    @Test
    void activeRoomUniqueKeyRejectsConflictAndRollsBackEarlierWrites() {
        Long firstLeaseId = leaseIds.get(0);
        Long secondLeaseId = leaseIds.get(1);
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "UPDATE lease_room SET status = 'ACTIVE', occupancy_start_at = NOW() WHERE lease_id = ?",
                    firstLeaseId
            );
            jdbcTemplate.update("UPDATE building_room SET rent_status = 'RENTED' WHERE id = ?", roomId);
        });

        String markerEventId = UUID.randomUUID().toString();
        assertThrows(DuplicateKeyException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "INSERT INTO lease_event (event_id, lease_id, event_type, event_payload, operator_id) "
                            + "VALUES (?, ?, 'TEST_ROLLBACK', JSON_OBJECT(), ?)",
                    markerEventId,
                    secondLeaseId,
                    OPERATOR_ID
            );
            jdbcTemplate.update("UPDATE lease SET version = version + 100 WHERE id = ?", secondLeaseId);
            jdbcTemplate.update(
                    "UPDATE lease_room SET status = 'ACTIVE', occupancy_start_at = NOW() WHERE lease_id = ?",
                    secondLeaseId
            );
        }));

        assertEquals(1, countActiveOccupancies());
        assertEquals("PENDING_CHECKIN", leaseRoomStatus(secondLeaseId));
        assertEquals(0L, leaseVersion(secondLeaseId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lease_event WHERE event_id = ?",
                Integer.class,
                markerEventId
        ));
    }

    @Test
    void concurrentServiceCheckInCommitsOneOccupantAndLeavesLosingAggregateUntouched() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> checkInAfterBarrier(leaseIds.get(0), start));
            Future<Boolean> second = executor.submit(() -> checkInAfterBarrier(leaseIds.get(1), start));
            int successCount = (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, successCount);
            assertEquals(1, countActiveOccupancies());
            assertEquals("RENTED", jdbcTemplate.queryForObject(
                    "SELECT rent_status FROM building_room WHERE id = ?",
                    String.class,
                    roomId
            ));
            assertEquals(1, countForLeases("lease_status_history"));
            assertEquals(1, countForLeases("lease_event"));
            assertEquals(1, countAggregateRows("domain_audit_log"));
            assertEquals(1, countAggregateRows("outbox_event"));

            Long losingLeaseId = jdbcTemplate.queryForObject(
                    "SELECT lease_id FROM lease_room WHERE room_id = ? AND status = 'PENDING_CHECKIN' AND deleted = 0",
                    Long.class,
                    roomId
            );
            assertEquals("PENDING_CHECKIN", leaseStatus(losingLeaseId));
            assertEquals(0L, leaseVersion(losingLeaseId));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean checkInAfterBarrier(Long leaseId, CyclicBarrier start) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            transactionTemplate.execute(status -> leaseService.checkIn(
                    leaseId,
                    new LeaseActionRequest(null, null, "V37 concurrent integration test", 0L),
                    OPERATOR_ID
            ));
            return true;
        } catch (RuntimeException expectedConflict) {
            return false;
        }
    }

    private void createFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        jdbcTemplate.update(
                "INSERT INTO tenant (code, name, status, created_by, updated_by, remark) "
                        + "VALUES (?, ?, 'ENABLED', ?, ?, 'LOCAL V37 INTEGRATION TEST')",
                "TEST-V37-T-" + suffix,
                "V37 integration tenant " + suffix,
                OPERATOR_ID,
                OPERATOR_ID
        );
        tenantId = idByCode("tenant", "TEST-V37-T-" + suffix);

        jdbcTemplate.update(
                "INSERT INTO building (code, name, total_floor, status, created_by, updated_by, remark) "
                        + "VALUES (?, ?, 1, 'ENABLED', ?, ?, 'LOCAL V37 INTEGRATION TEST')",
                "TEST-V37-B-" + suffix,
                "V37 integration building " + suffix,
                OPERATOR_ID,
                OPERATOR_ID
        );
        buildingId = idByCode("building", "TEST-V37-B-" + suffix);
        jdbcTemplate.update(
                "INSERT INTO building_floor (code, building_id, floor_no, name, status, created_by, updated_by) "
                        + "VALUES (?, ?, 1, 'V37 test floor', 'ENABLED', ?, ?)",
                "TEST-V37-F-" + suffix,
                buildingId,
                OPERATOR_ID,
                OPERATOR_ID
        );
        floorId = idByCode("building_floor", "TEST-V37-F-" + suffix);
        jdbcTemplate.update(
                "INSERT INTO building_room (code, building_id, floor_id, room_no, area, usage_type, rent_status, "
                        + "status, created_by, updated_by) VALUES (?, ?, ?, ?, 1, 'OFFICE', 'VACANT', 'ENABLED', ?, ?)",
                "TEST-V37-R-" + suffix,
                buildingId,
                floorId,
                "V37-" + suffix,
                OPERATOR_ID,
                OPERATOR_ID
        );
        roomId = idByCode("building_room", "TEST-V37-R-" + suffix);

        for (int index = 1; index <= 2; index++) {
            String contractCode = "TEST-V37-C" + index + "-" + suffix;
            jdbcTemplate.update(
                    """
                    INSERT INTO contract (
                        code, tenant_id, room_id, start_date, end_date, effective_date,
                        currency, current_revision_no, rent_amount, deposit_amount,
                        status, version, created_by, updated_by, remark
                    ) VALUES (?, ?, ?, '2026-01-01', '2099-12-31', '2026-01-01',
                              'CNY', 1, 1, 0, 'ACTIVE', 0, ?, ?, 'LOCAL V37 INTEGRATION TEST')
                    """,
                    contractCode,
                    tenantId,
                    roomId,
                    OPERATOR_ID,
                    OPERATOR_ID
            );
            Long contractId = idByCode("contract", contractCode);
            contractIds.add(contractId);
            jdbcTemplate.update(
                    "INSERT INTO contract_room (contract_id, room_id, created_by, updated_by) VALUES (?, ?, ?, ?)",
                    contractId,
                    roomId,
                    OPERATOR_ID,
                    OPERATOR_ID
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO contract_revision (
                        contract_id, revision_no, rent_terms_snapshot, deposit_terms_snapshot,
                        billing_terms_snapshot, change_reason, effective_from, created_by, updated_by
                    ) VALUES (?, 1, JSON_OBJECT(), JSON_OBJECT(), JSON_OBJECT(),
                              'LOCAL V37 INTEGRATION TEST', '2026-01-01', ?, ?)
                    """,
                    contractId,
                    OPERATOR_ID,
                    OPERATOR_ID
            );
            Long revisionId = jdbcTemplate.queryForObject(
                    "SELECT id FROM contract_revision WHERE contract_id = ? AND revision_no = 1",
                    Long.class,
                    contractId
            );
            String leaseNo = "TEST-V37-L" + index + "-" + suffix;
            jdbcTemplate.update(
                    """
                    INSERT INTO lease (
                        lease_no, tenant_id, contract_id, contract_revision_id, status,
                        planned_start_date, planned_end_date, version, created_by, updated_by, remark
                    ) VALUES (?, ?, ?, ?, 'PENDING_CHECKIN', '2026-01-01', '2099-12-31', 0, ?, ?,
                              'LOCAL V37 INTEGRATION TEST')
                    """,
                    leaseNo,
                    tenantId,
                    contractId,
                    revisionId,
                    OPERATOR_ID,
                    OPERATOR_ID
            );
            Long leaseId = jdbcTemplate.queryForObject(
                    "SELECT id FROM lease WHERE lease_no = ?",
                    Long.class,
                    leaseNo
            );
            leaseIds.add(leaseId);
            jdbcTemplate.update(
                    """
                    INSERT INTO lease_room (
                        lease_id, room_id, status, room_code_snapshot, room_name_snapshot,
                        building_name_snapshot, floor_name_snapshot, version, created_by, updated_by
                    ) VALUES (?, ?, 'PENDING_CHECKIN', ?, ?, ?, 'V37 test floor', 0, ?, ?)
                    """,
                    leaseId,
                    roomId,
                    "TEST-V37-R-" + suffix,
                    "V37-" + suffix,
                    "V37 integration building " + suffix,
                    OPERATOR_ID,
                    OPERATOR_ID
            );
        }
    }

    private int countActiveOccupancies() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lease_room WHERE room_id = ? AND active_room_id = ?",
                Integer.class,
                roomId,
                roomId
        );
    }

    private int countForLeases(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE lease_id IN (?, ?)",
                Integer.class,
                leaseIds.get(0),
                leaseIds.get(1)
        );
    }

    private int countAggregateRows(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE aggregate_type = 'Lease' AND aggregate_id IN (?, ?)",
                Integer.class,
                leaseIds.get(0),
                leaseIds.get(1)
        );
    }

    private String leaseRoomStatus(Long leaseId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM lease_room WHERE lease_id = ? AND room_id = ?",
                String.class,
                leaseId,
                roomId
        );
    }

    private String leaseStatus(Long leaseId) {
        return jdbcTemplate.queryForObject("SELECT status FROM lease WHERE id = ?", String.class, leaseId);
    }

    private long leaseVersion(Long leaseId) {
        Long version = jdbcTemplate.queryForObject("SELECT version FROM lease WHERE id = ?", Long.class, leaseId);
        return version == null ? -1 : version;
    }

    private Long idByCode(String table, String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM " + table + " WHERE code = ?", Long.class, code);
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
}
