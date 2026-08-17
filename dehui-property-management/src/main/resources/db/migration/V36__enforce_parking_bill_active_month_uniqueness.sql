ALTER TABLE parking_bill
    ADD COLUMN active_billing_month CHAR(7)
    GENERATED ALWAYS AS (
        CASE
            WHEN deleted = 0 AND status IN ('ACTIVE', 'ENABLED') THEN billing_month
            ELSE NULL
        END
    ) STORED,
    ADD UNIQUE KEY uk_parking_bill_active_month
        (assignment_id, active_billing_month);
