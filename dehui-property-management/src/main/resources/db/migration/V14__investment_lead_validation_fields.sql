ALTER TABLE investment_lead
  ADD COLUMN intended_room VARCHAR(64) DEFAULT NULL COMMENT '意向房源' AFTER company_name;
