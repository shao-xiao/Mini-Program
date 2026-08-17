-- DHIC 第四阶段A：统一字段 Schema、surface 配置和内部文件归属。
-- 仅为现有 ModuleFieldRegistry 增加配置覆盖，不创建平行业务字段注册体系。

CREATE TABLE module_field_setting (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    module_key VARCHAR(64) NOT NULL COMMENT 'ModuleFieldRegistry 模块键',
    field_key VARCHAR(96) NOT NULL COMMENT '稳定系统字段键',
    label_override VARCHAR(100) NULL COMMENT '显示名称覆盖',
    description VARCHAR(500) NULL COMMENT '字段说明',
    required_override TINYINT(1) NULL COMMENT '必填覆盖；不得弱化系统必填',
    validation_json JSON NULL COMMENT '校验规则覆盖',
    surfaces_json JSON NULL COMMENT '五种 surface 独立显示/编辑/排序',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观版本',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_field_setting (module_key, field_key),
    KEY idx_module_field_setting_module (module_key, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='系统模块字段 surface 配置覆盖';

ALTER TABLE file_upload
    ADD COLUMN workspace_key VARCHAR(64) NULL
        COMMENT '自定义字段所属工作区' AFTER biz_id,
    ADD COLUMN owner_mobile_user_id BIGINT NULL
        COMMENT '移动端草稿文件所属 mobile_user' AFTER owner_sys_user_id,
    ADD KEY idx_file_workspace_record (workspace_key, biz_id, deleted),
    ADD KEY idx_file_mobile_owner (access_scope, owner_mobile_user_id, created_at, deleted);
