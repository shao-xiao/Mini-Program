-- LOCAL DEVELOPMENT ONLY
-- 仅允许在 dehui_property_dev 执行
-- Snapshot captured on 2026-07-16. Any drift makes this script a no-op.

SELECT DATABASE() AS current_database, VERSION() AS mysql_version;

SET @expected_database := 'dehui_property_dev';
SET @expected_bill_count := 510;
SET @expected_assignment_count := 502;

SET @bill_fingerprint := (
    SELECT GROUP_CONCAT(
        CONCAT(id, ':', assignment_id, ':', amount, ':', IFNULL(billing_month, 'NULL'),
               ':', pay_status, ':', status, ':', deleted)
        ORDER BY id SEPARATOR '|'
    )
    FROM parking_bill
    WHERE id IN (1, 2, 1503, 1504, 1505, 1506, 1507, 1508, 1509, 1510)
);

SET @assignment_fingerprint := (
    SELECT GROUP_CONCAT(
        CONCAT(id, ':', parking_space_id, ':', tenant_id, ':', IFNULL(monthly_fee, 'NULL'),
               ':', status, ':', deleted)
        ORDER BY id SEPARATOR '|'
    )
    FROM parking_assignment
    WHERE id IN (1, 2)
);

SET @external_fk_count := (
    SELECT COUNT(*)
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND REFERENCED_TABLE_NAME IN ('parking_bill', 'parking_assignment')
      AND TABLE_NAME NOT IN ('parking_bill', 'parking_assignment')
);

SET @snapshot_matches := (
    (SELECT COUNT(*) FROM parking_bill) = @expected_bill_count
    AND (SELECT COUNT(*) FROM parking_bill WHERE deleted = 0) = 10
    AND (SELECT COUNT(*) FROM parking_bill WHERE deleted <> 0) = 500
    AND (SELECT COUNT(*) FROM parking_bill WHERE billing_month IS NULL) = 510
    AND (SELECT COUNT(*) FROM parking_assignment) = @expected_assignment_count
    AND (SELECT COUNT(*) FROM parking_assignment WHERE deleted = 0) = 2
    AND (SELECT COUNT(*) FROM parking_assignment WHERE deleted <> 0) = 500
    AND (SELECT COUNT(*) FROM parking_assignment WHERE monthly_fee IS NULL) = 502
    AND @bill_fingerprint =
        '1:1:500.00:NULL:UNPAID:ACTIVE:0|2:2:650.00:NULL:PAID:ACTIVE:0|1503:1:0.00:NULL:UNPAID:ACTIVE:0|1504:2:0.00:NULL:UNPAID:ACTIVE:0|1505:1:0.00:NULL:UNPAID:ACTIVE:0|1506:2:0.00:NULL:UNPAID:ACTIVE:0|1507:1:0.00:NULL:UNPAID:ACTIVE:0|1508:2:0.00:NULL:UNPAID:ACTIVE:0|1509:1:0.00:NULL:PAID:ACTIVE:0|1510:2:0.00:NULL:PAID:ACTIVE:0'
    AND @assignment_fingerprint =
        '1:1:6:NULL:ACTIVE:0|2:3:8:NULL:ACTIVE:0'
    AND @external_fk_count = 0
);

SET @can_reset := DATABASE() = @expected_database AND @snapshot_matches = 1;

SELECT CASE
           WHEN DATABASE() <> @expected_database THEN 'BLOCKED_WRONG_DATABASE'
           WHEN @external_fk_count <> 0 THEN 'BLOCKED_EXTERNAL_REFERENCE'
           WHEN @snapshot_matches <> 1 THEN 'BLOCKED_SNAPSHOT_CHANGED'
           ELSE 'READY_TO_RESET'
       END AS precheck_status,
       @external_fk_count AS external_fk_count,
       (SELECT COUNT(*) FROM parking_bill) AS current_bill_count,
       (SELECT COUNT(*) FROM parking_assignment) AS current_assignment_count;

START TRANSACTION;

UPDATE parking_space
SET rent_status = 'AVAILABLE'
WHERE @can_reset = 1
  AND id IN (
      SELECT parking_space_id
      FROM parking_assignment
      WHERE deleted = 0
  );
SET @released_space_count := ROW_COUNT();

DELETE FROM parking_bill
WHERE @can_reset = 1;
SET @deleted_bill_count := ROW_COUNT();

DELETE FROM parking_assignment
WHERE @can_reset = 1;
SET @deleted_assignment_count := ROW_COUNT();

COMMIT;

SELECT CASE WHEN @can_reset = 1 THEN 'RESET_COMPLETED' ELSE 'RESET_NOT_EXECUTED' END AS reset_status,
       @released_space_count AS released_space_count,
       @deleted_bill_count AS deleted_bill_count,
       @deleted_assignment_count AS deleted_assignment_count,
       (SELECT COUNT(*) FROM parking_bill) AS remaining_bill_count,
       (SELECT COUNT(*) FROM parking_assignment) AS remaining_assignment_count;
