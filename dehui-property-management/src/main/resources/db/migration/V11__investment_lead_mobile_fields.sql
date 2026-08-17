ALTER TABLE investment_lead
  ADD COLUMN preferred_visit_time VARCHAR(64) DEFAULT NULL COMMENT '期望看房时间' AFTER intended_use;
