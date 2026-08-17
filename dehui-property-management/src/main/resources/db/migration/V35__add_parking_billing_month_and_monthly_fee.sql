ALTER TABLE parking_bill
    ADD COLUMN billing_month CHAR(7) NULL
    COMMENT '收费所属月份，格式 YYYY-MM';

ALTER TABLE parking_assignment
    ADD COLUMN monthly_fee DECIMAL(10,2) NULL
    COMMENT '停车月租金额';
