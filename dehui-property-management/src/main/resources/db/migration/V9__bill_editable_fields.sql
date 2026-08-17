ALTER TABLE bill
  ADD COLUMN title VARCHAR(200) DEFAULT NULL COMMENT '账单标题' AFTER bill_type,
  ADD COLUMN period_start DATE DEFAULT NULL COMMENT '账期开始日期' AFTER paid_amount,
  ADD COLUMN period_end DATE DEFAULT NULL COMMENT '账期结束日期' AFTER period_start,
  ADD KEY idx_bill_period (period_start, period_end);
