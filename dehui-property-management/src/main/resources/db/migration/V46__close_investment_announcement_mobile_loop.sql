-- DHIC 招商展示、真实房源、预约线索与园区公告闭环。
-- 仅追加字段、索引和状态历史，不删除历史业务记录。

ALTER TABLE investment_display
    ADD COLUMN public_visible TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '是否允许公开展示' AFTER image_url,
    ADD COLUMN effective_at DATETIME NULL
        COMMENT '公开生效时间' AFTER public_visible,
    ADD COLUMN expires_at DATETIME NULL
        COMMENT '公开失效时间' AFTER effective_at;

CREATE INDEX idx_investment_display_public_window
    ON investment_display (publish_status, public_visible, effective_at, expires_at, deleted, sort_order);

-- 历史“招商公告”区块不再进入招商中心；记录保留，仅停止公开。
UPDATE investment_display
SET publish_status = 'DISABLED',
    updated_by = 0,
    remark = CONCAT_WS('；', NULLIF(remark, ''), 'V46：招商展示与园区公告分离，历史招商公告区块停止公开')
WHERE section_key = 'notice'
  AND deleted = 0
  AND publish_status = 'PUBLISHED';

ALTER TABLE investment_room_listing
    ADD COLUMN display_area_snapshot DECIMAL(10,2) NULL
        COMMENT '发布展示面积快照，真实关系仍以room_id为准' AFTER room_id,
    ADD COLUMN is_reservable TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '营销配置是否允许预约，最终资格同时校验真实房间' AFTER is_recommended,
    ADD COLUMN effective_at DATETIME NULL
        COMMENT '房源公开生效时间' AFTER sort_order,
    ADD COLUMN expires_at DATETIME NULL
        COMMENT '房源公开失效时间' AFTER effective_at;

UPDATE investment_room_listing listing_record
JOIN building_room room_record
  ON room_record.id = listing_record.room_id
SET listing_record.display_area_snapshot = room_record.area
WHERE listing_record.display_area_snapshot IS NULL;

CREATE INDEX idx_investment_listing_public_window
    ON investment_room_listing (
        publish_status, is_reservable, effective_at, expires_at, deleted, sort_order
    );

ALTER TABLE investment_lead
    ADD COLUMN mobile_user_id BIGINT NULL
        COMMENT '提交预约的小程序用户ID，服务端从技术会话取得' AFTER listing_id,
    ADD COLUMN listing_title_snapshot VARCHAR(100) NULL
        COMMENT '预约时房源展示标题快照' AFTER mobile_user_id,
    ADD COLUMN building_name_snapshot VARCHAR(128) NULL
        COMMENT '预约时楼宇名称快照' AFTER listing_title_snapshot,
    ADD COLUMN floor_snapshot VARCHAR(64) NULL
        COMMENT '预约时楼层快照' AFTER building_name_snapshot,
    ADD COLUMN room_code_snapshot VARCHAR(64) NULL
        COMMENT '预约时房号快照' AFTER floor_snapshot,
    ADD COLUMN area_snapshot DECIMAL(10,2) NULL
        COMMENT '预约时展示面积快照' AFTER room_code_snapshot,
    ADD COLUMN status_changed_at DATETIME NULL
        COMMENT '最近状态变更时间' AFTER lead_status,
    ADD COLUMN status_changed_by BIGINT NULL
        COMMENT '最近状态变更后台用户' AFTER status_changed_at;

UPDATE investment_lead
SET mobile_user_id = created_by
WHERE source = 'MINIPROGRAM'
  AND mobile_user_id IS NULL
  AND created_by IS NOT NULL
  AND created_by > 0;

CREATE INDEX idx_investment_lead_mobile_user
    ON investment_lead (mobile_user_id, created_at, deleted);

CREATE INDEX idx_investment_lead_listing_submit
    ON investment_lead (listing_id, mobile_user_id, created_at, deleted);

CREATE TABLE investment_lead_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    lead_id BIGINT NOT NULL COMMENT '招商线索ID',
    from_status VARCHAR(32) NULL COMMENT '原状态',
    to_status VARCHAR(32) NOT NULL COMMENT '目标状态',
    operator_id BIGINT NOT NULL DEFAULT 0 COMMENT '后台操作人ID',
    changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    remark VARCHAR(500) NULL COMMENT '变更说明',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '记录状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_investment_lead_status_history (lead_id, changed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='招商线索状态历史';

ALTER TABLE notice
    ADD COLUMN summary VARCHAR(500) NULL
        COMMENT '公告摘要' AFTER title,
    ADD COLUMN cover_image_url VARCHAR(500) NULL
        COMMENT '公告封面图' AFTER content,
    ADD COLUMN carousel_sort INT NOT NULL DEFAULT 0
        COMMENT '首页轮播排序' AFTER show_on_home,
    ADD COLUMN effective_at DATETIME NULL
        COMMENT '公开生效时间' AFTER carousel_sort,
    ADD COLUMN expires_at DATETIME NULL
        COMMENT '公开失效时间' AFTER effective_at,
    ADD COLUMN visibility_scope VARCHAR(32) NOT NULL DEFAULT 'PUBLIC'
        COMMENT '可见范围，当前公开公告为PUBLIC' AFTER expires_at;

UPDATE notice
SET notice_type = CASE
    WHEN notice_type IN ('NOTICE', 'ACTIVITY', 'PROPERTY', 'REMINDER') THEN notice_type
    ELSE 'NOTICE'
END
WHERE deleted = 0;

CREATE INDEX idx_notice_public_window
    ON notice (
        visibility_scope, publish_status, effective_at, expires_at, deleted, published_at
    );

CREATE INDEX idx_notice_carousel_order
    ON notice (
        show_on_home, carousel_sort, publish_status, visibility_scope, deleted, published_at
    );
