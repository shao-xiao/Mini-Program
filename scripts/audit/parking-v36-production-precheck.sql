-- READ ONLY: parking V36 production precheck.
-- This script must never change production data or schema.

SELECT DATABASE() AS database_name, VERSION() AS mysql_version;

SELECT installed_rank, version, description, type, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, EXTRA, GENERATION_EXPRESSION
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('parking_bill', 'parking_assignment')
  AND COLUMN_NAME IN ('billing_month', 'monthly_fee', 'active_billing_month')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

SELECT CASE
           WHEN (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'parking_bill'
                   AND COLUMN_NAME = 'billing_month') = 1
            AND (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'parking_assignment'
                   AND COLUMN_NAME = 'monthly_fee') = 1
               THEN 'READY_FOR_FULL_V36_PRECHECK'
           ELSE 'BLOCKED_V35_FIELDS_MISSING'
       END AS v35_prerequisite_status;

-- Stop here when the V35 prerequisite is blocked. The remaining read-only
-- statements intentionally reference the V35 columns and must not be run first.

SELECT COUNT(*) AS malformed_nonnull_billing_month_count
FROM parking_bill
WHERE billing_month IS NOT NULL
  AND billing_month NOT REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$';

SELECT assignment_id, billing_month, GROUP_CONCAT(id ORDER BY id) AS bill_ids, COUNT(*) AS record_count
FROM parking_bill
WHERE deleted = 0
  AND status IN ('ACTIVE', 'ENABLED')
  AND billing_month IS NOT NULL
GROUP BY assignment_id, billing_month
HAVING COUNT(*) > 1;

SELECT COUNT(*) AS expected_unique_conflict_group_count
FROM (
    SELECT assignment_id, billing_month
    FROM parking_bill
    WHERE deleted = 0
      AND status IN ('ACTIVE', 'ENABLED')
      AND billing_month IS NOT NULL
    GROUP BY assignment_id, billing_month
    HAVING COUNT(*) > 1
) conflicts;

SELECT COUNT(*) AS active_bill_missing_assignment_count
FROM parking_bill
WHERE deleted = 0
  AND status IN ('ACTIVE', 'ENABLED')
  AND assignment_id IS NULL;

SELECT COUNT(*) AS active_history_without_billing_month_count
FROM parking_bill
WHERE deleted = 0
  AND status IN ('ACTIVE', 'ENABLED')
  AND billing_month IS NULL;

SELECT COLUMN_NAME, COLUMN_TYPE, EXTRA, GENERATION_EXPRESSION
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'parking_bill'
  AND COLUMN_NAME = 'active_billing_month';

SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'parking_bill'
  AND INDEX_NAME = 'uk_parking_bill_active_month'
ORDER BY SEQ_IN_INDEX;

SELECT COUNT(*) AS active_assignment_count,
       SUM(monthly_fee IS NOT NULL AND monthly_fee > 0) AS configured_positive_fee_count,
       SUM(monthly_fee IS NULL OR monthly_fee <= 0) AS missing_or_invalid_fee_count
FROM parking_assignment
WHERE deleted = 0
  AND status IN ('ACTIVE', 'ENABLED');

SELECT status, COUNT(*) AS record_count
FROM parking_bill
GROUP BY status
ORDER BY status;

SELECT pay_status, COUNT(*) AS record_count
FROM parking_bill
GROUP BY pay_status
ORDER BY pay_status;

SELECT deleted, COUNT(*) AS record_count
FROM parking_bill
GROUP BY deleted
ORDER BY deleted;

SELECT CASE
           WHEN (SELECT COUNT(*)
                 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'parking_bill'
                   AND COLUMN_NAME = 'billing_month') <> 1
               THEN 'BLOCKED'
           WHEN (SELECT COUNT(*)
                 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'parking_assignment'
                   AND COLUMN_NAME = 'monthly_fee') <> 1
               THEN 'BLOCKED'
           WHEN (SELECT COUNT(*) FROM parking_bill
                 WHERE billing_month IS NOT NULL
                   AND billing_month NOT REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$') <> 0
               THEN 'BLOCKED'
           WHEN (SELECT COUNT(*) FROM (
                     SELECT assignment_id, billing_month
                     FROM parking_bill
                     WHERE deleted = 0
                       AND status IN ('ACTIVE', 'ENABLED')
                       AND billing_month IS NOT NULL
                     GROUP BY assignment_id, billing_month
                     HAVING COUNT(*) > 1
                 ) duplicate_months) <> 0
               THEN 'BLOCKED'
           WHEN (SELECT COUNT(*) FROM parking_bill
                 WHERE deleted = 0
                   AND status IN ('ACTIVE', 'ENABLED')
                   AND assignment_id IS NULL) <> 0
               THEN 'BLOCKED'
           WHEN (SELECT COUNT(*) FROM parking_assignment
                 WHERE deleted = 0
                   AND status IN ('ACTIVE', 'ENABLED')
                   AND (monthly_fee IS NULL OR monthly_fee <= 0)) <> 0
               THEN 'BLOCKED'
           WHEN (SELECT COUNT(*) FROM parking_bill
                 WHERE deleted = 0
                   AND status NOT IN ('ACTIVE', 'ENABLED', 'VOID')) <> 0
               THEN 'BLOCKED'
           WHEN (SELECT COUNT(*) FROM parking_bill
                 WHERE pay_status NOT IN ('UNPAID', 'PAID')) <> 0
               THEN 'BLOCKED'
           WHEN ((SELECT COUNT(*) FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = 'parking_bill'
                    AND COLUMN_NAME = 'active_billing_month') = 0)
                <> ((SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 'parking_bill'
                       AND INDEX_NAME = 'uk_parking_bill_active_month') = 0)
               THEN 'BLOCKED'
           ELSE 'READY'
       END AS parking_v36_precheck_status;
