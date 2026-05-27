ALTER TABLE investment_display
  ADD COLUMN section_key VARCHAR(64) NOT NULL DEFAULT 'highlight' COMMENT '小程序招商中心展示区块' AFTER code,
  ADD COLUMN subtitle VARCHAR(255) DEFAULT NULL COMMENT '副标题' AFTER title,
  ADD COLUMN image_url VARCHAR(500) DEFAULT NULL COMMENT '图片地址' AFTER cover_file_id;

UPDATE investment_display
SET section_key = CASE
  WHEN title LIKE '%主标题%' OR title LIKE '%招商中心%' OR title LIKE '%主视觉%' THEN 'hero'
  WHEN title LIKE '%政策%' THEN 'policy'
  WHEN title LIKE '%介绍%' THEN 'introduction'
  WHEN title LIKE '%地址%' OR title LIKE '%位置%' THEN 'location'
  WHEN title LIKE '%联系%' OR title LIKE '%电话%' THEN 'contact'
  WHEN title LIKE '%公告%' OR title LIKE '%通知%' THEN 'notice'
  ELSE 'highlight'
END
WHERE deleted = 0;

CREATE INDEX idx_invest_display_section_publish_sort
  ON investment_display(section_key, publish_status, sort_order);
