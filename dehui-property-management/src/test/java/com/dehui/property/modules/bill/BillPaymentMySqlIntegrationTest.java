package com.dehui.property.modules.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.bill.application.BillCommandService;
import java.math.BigDecimal;
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

/** Destructive local-only payment state-machine coverage. */
@EnabledIfEnvironmentVariable(named = "BILL_DB_INTEGRATION", matches = "true")
class BillPaymentMySqlIntegrationTest {

    private static final long OPERATOR_ID = 72_001L;

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private BillCommandService service;
    private Long tenantId;
    private final List<Long> billIds = new ArrayList<>();

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
        service = new BillCommandService(jdbcTemplate);
        tenantId = jdbcTemplate.queryForObject(
                "SELECT id FROM tenant WHERE deleted = 0 AND status IN ('ACTIVE', 'ENABLED') ORDER BY id LIMIT 1",
                Long.class
        );
    }

    @AfterEach
    void tearDown() {
        if (jdbcTemplate == null) return;
        for (Long billId : billIds) {
            jdbcTemplate.update("DELETE FROM bill_payment_record WHERE bill_id = ?", billId);
            jdbcTemplate.update("DELETE FROM bill WHERE id = ?", billId);
        }
    }

    @Test
    void legalPaymentCommitsOnceAndSecondCallConflicts() {
        Long billId = createApprovedBill("120.00");
        inTransaction(() -> service.confirmOfflinePayment(billId, OPERATOR_ID));

        BusinessException repeated = assertThrows(
                BusinessException.class,
                () -> inTransaction(() -> service.confirmOfflinePayment(billId, OPERATOR_ID))
        );

        assertEquals(409, repeated.getCode());
        assertEquals("PAID", payStatus(billId));
        assertEquals(1, paymentCount(billId));
        assertEquals(OPERATOR_ID, jdbcTemplate.queryForObject(
                "SELECT created_by FROM bill_payment_record WHERE bill_id = ? AND deleted = 0",
                Long.class,
                billId
        ));
    }

    @Test
    void concurrentPaymentsCommitExactlyOneRecord() throws Exception {
        Long billId = createApprovedBill("180.00");
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> attemptPayment(billId, barrier));
            Future<Boolean> second = executor.submit(() -> attemptPayment(billId, barrier));
            int successes = (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, successes);
            assertEquals("PAID", payStatus(billId));
            assertEquals(1, paymentCount(billId));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void paymentInsertFailureRollsBackBillState() {
        Long blockerBillId = createApprovedBill("50.00");
        Long targetBillId = createApprovedBill("90.00");
        String duplicateCode = "TEST-PAY-DUP-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update(
                """
                INSERT INTO bill_payment_record
                    (code, bill_id, tenant_id, pay_channel, amount, paid_at, status, created_by, updated_by)
                VALUES (?, ?, ?, 'OFFLINE', 50.00, NOW(), 'PAID', ?, ?)
                """,
                duplicateCode,
                blockerBillId,
                tenantId,
                OPERATOR_ID,
                OPERATOR_ID
        );
        BillCommandService failingService = new BillCommandService(jdbcTemplate) {
            @Override
            protected String nextPaymentCode() {
                return duplicateCode;
            }
        };

        assertThrows(
                DuplicateKeyException.class,
                () -> inTransaction(() -> failingService.confirmOfflinePayment(targetBillId, OPERATOR_ID))
        );

        assertEquals("UNPAID", payStatus(targetBillId));
        assertEquals(0, paymentCount(targetBillId));
    }

    private boolean attemptPayment(Long billId, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            inTransaction(() -> service.confirmOfflinePayment(billId, OPERATOR_ID));
            return true;
        } catch (BusinessException exception) {
            if (exception.getCode() == 409) return false;
            throw exception;
        }
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                .executeWithoutResult(status -> action.run());
    }

    private Long createApprovedBill(String amount) {
        String code = "TEST-BILL-PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbcTemplate.update(
                """
                INSERT INTO bill
                    (code, tenant_id, source_type, bill_type, amount, final_amount, paid_amount, due_date,
                     audit_status, workflow_status, pay_status, status,
                     created_by, updated_by, deleted, remark)
                VALUES (?, ?, 'MANUAL', 'OTHER', ?, ?, 0, CURRENT_DATE,
                        'APPROVED', 'APPROVED', 'UNPAID', 'ACTIVE',
                        ?, ?, 0, 'LOCAL PAYMENT INTEGRATION TEST')
                """,
                code,
                tenantId,
                new BigDecimal(amount),
                new BigDecimal(amount),
                OPERATOR_ID,
                OPERATOR_ID
        );
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM bill WHERE code = ? AND deleted = 0",
                Long.class,
                code
        );
        billIds.add(id);
        return id;
    }

    private String payStatus(Long billId) {
        return jdbcTemplate.queryForObject("SELECT pay_status FROM bill WHERE id = ?", String.class, billId);
    }

    private int paymentCount(Long billId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bill_payment_record WHERE bill_id = ? AND deleted = 0",
                Integer.class,
                billId
        );
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    private static void requireDevelopmentJdbcUrl(String jdbcUrl) {
        if (!jdbcUrl.matches("(?i)^jdbc:mysql://[^/]+/dehui_property_dev(?:\\?.*)?$")) {
            throw new IllegalStateException("Bill integration tests only allow dehui_property_dev");
        }
    }

    private static void assertDevelopmentDatabase(DriverManagerDataSource source) throws Exception {
        try (Connection connection = source.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT DATABASE()")) {
            resultSet.next();
            assertEquals("dehui_property_dev", resultSet.getString(1));
        }
    }
}
