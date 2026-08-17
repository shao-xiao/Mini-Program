CREATE TABLE IF NOT EXISTS investment_room_listing (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
  room_id BIGINT NOT NULL COMMENT 'Linked room id',
  title VARCHAR(100) DEFAULT NULL COMMENT 'Display title',
  subtitle VARCHAR(200) DEFAULT NULL COMMENT 'Display subtitle',
  description TEXT DEFAULT NULL COMMENT 'Listing description',
  highlights TEXT DEFAULT NULL COMMENT 'Highlights JSON or newline text',
  tags TEXT DEFAULT NULL COMMENT 'Tags JSON or newline text',
  rent_desc VARCHAR(500) DEFAULT NULL COMMENT 'Rent description',
  property_fee_desc VARCHAR(500) DEFAULT NULL COMMENT 'Property fee description',
  cover_image_url VARCHAR(500) DEFAULT NULL COMMENT 'Cover image url',
  publish_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/OFFLINE',
  is_recommended TINYINT NOT NULL DEFAULT 0 COMMENT 'Recommended flag',
  sort_order INT NOT NULL DEFAULT 0 COMMENT 'Sort order',
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Record status',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT DEFAULT 0,
  updated_by BIGINT DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) DEFAULT NULL,
  KEY idx_investment_room_listing_room (room_id),
  KEY idx_investment_room_listing_publish (publish_status, deleted, sort_order)
) COMMENT='Investment room listing';

CREATE TABLE IF NOT EXISTS investment_room_image (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
  listing_id BIGINT NOT NULL COMMENT 'Investment listing id',
  room_id BIGINT DEFAULT NULL COMMENT 'Linked room id',
  image_url VARCHAR(500) NOT NULL COMMENT 'Image url',
  image_type VARCHAR(30) NOT NULL DEFAULT 'GALLERY' COMMENT 'COVER/GALLERY',
  sort_order INT NOT NULL DEFAULT 0 COMMENT 'Sort order',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT DEFAULT 0,
  updated_by BIGINT DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_investment_room_image_listing (listing_id, deleted, sort_order),
  KEY idx_investment_room_image_room (room_id)
) COMMENT='Investment room images';

ALTER TABLE investment_lead
  ADD COLUMN room_id BIGINT DEFAULT NULL COMMENT 'Linked room id' AFTER intended_room,
  ADD COLUMN listing_id BIGINT DEFAULT NULL COMMENT 'Linked investment listing id' AFTER room_id;

SET @menu_lease = (SELECT id FROM sys_menu WHERE code = 'lease' AND deleted = 0 LIMIT 1);

INSERT INTO sys_menu (code, parent_id, name, path, component, icon, sort_order, status, created_by, updated_by, remark)
VALUES ('lease:investment-listing', @menu_lease, '招商房源', '/investment/listings', NULL, NULL, 32, 'ENABLED', 0, 0, '招商房源展示配置')
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  name = VALUES(name),
  path = VALUES(path),
  sort_order = VALUES(sort_order),
  status = VALUES(status),
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

UPDATE sys_menu
SET sort_order = 33
WHERE code = 'lease:investment-lead' AND deleted = 0;

UPDATE sys_menu
SET sort_order = 34
WHERE code = 'lease:contract' AND deleted = 0;

UPDATE sys_menu
SET sort_order = 35
WHERE code = 'lease:tenant' AND deleted = 0;

UPDATE sys_menu
SET sort_order = 36
WHERE code = 'lease:checkin' AND deleted = 0;

SET @admin_role_id = (SELECT id FROM sys_role WHERE code = 'ADMIN' AND deleted = 0 LIMIT 1);
SET @listing_menu_id = (SELECT id FROM sys_menu WHERE code = 'lease:investment-listing' AND deleted = 0 LIMIT 1);

INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT @admin_role_id, @listing_menu_id, 'ENABLED', 0, 0
WHERE @admin_role_id IS NOT NULL AND @listing_menu_id IS NOT NULL
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;
