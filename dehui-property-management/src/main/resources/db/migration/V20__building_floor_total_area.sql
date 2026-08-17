ALTER TABLE building_floor
  ADD COLUMN total_area DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '楼层总面积' AFTER name;
