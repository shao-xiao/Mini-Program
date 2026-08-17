-- Parking billing V35 preflight audit. Read-only by design.

-- 1. Duplicate candidate billing periods for active records.
SELECT assignment_id,
       DATE_FORMAT(due_date, '%Y-%m') AS candidate_billing_month,
       GROUP_CONCAT(id ORDER BY id) AS bill_ids,
       COUNT(*) AS record_count
FROM parking_bill
WHERE deleted = 0
  AND due_date IS NOT NULL
GROUP BY assignment_id, DATE_FORMAT(due_date, '%Y-%m')
HAVING COUNT(*) > 1;

-- 2. Active zero or negative amount records.
SELECT id, assignment_id, code, amount, pay_status, status, due_date
FROM parking_bill
WHERE deleted = 0
  AND amount <= 0
ORDER BY assignment_id, id;

-- 3. Paid zero or negative amount records.
SELECT id, assignment_id, code, amount, pay_status, status, due_date
FROM parking_bill
WHERE deleted = 0
  AND pay_status = 'PAID'
  AND amount <= 0
ORDER BY assignment_id, id;

-- 4. Missing due dates or assignment identifiers.
SELECT id, assignment_id, code, pay_status, status, due_date
FROM parking_bill
WHERE due_date IS NULL
   OR assignment_id IS NULL
ORDER BY id;

-- 5. Orphaned assignment relationships.
SELECT pb.id, pb.assignment_id, pb.code, pb.pay_status, pb.status
FROM parking_bill pb
LEFT JOIN parking_assignment pa ON pa.id = pb.assignment_id
WHERE pa.id IS NULL
ORDER BY pb.id;

-- 6. Unexpected state combinations.
SELECT id, assignment_id, code, amount, pay_status, status, deleted
FROM parking_bill
WHERE pay_status NOT IN ('UNPAID', 'PAID')
   OR status NOT IN ('ACTIVE', 'ENABLED', 'VOID')
   OR (pay_status = 'PAID' AND status = 'VOID')
ORDER BY id;

-- 7. Rows without a candidate period. due_date is only a review candidate.
SELECT id, assignment_id, code, due_date, created_at
FROM parking_bill
WHERE billing_month IS NULL
  AND due_date IS NULL
ORDER BY id;

-- 8. Active assignments requiring manual monthly-fee configuration.
SELECT id, code, parking_space_id, tenant_id, start_date, end_date, status, monthly_fee
FROM parking_assignment
WHERE deleted = 0
  AND status IN ('ACTIVE', 'ENABLED')
  AND (monthly_fee IS NULL OR monthly_fee <= 0)
ORDER BY id;
