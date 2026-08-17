CREATE TABLE work_order_mobile_notification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  work_order_id BIGINT NOT NULL COMMENT '工单ID',
  recipient_user_id BIGINT NOT NULL COMMENT '接收内部用户ID',
  action VARCHAR(32) NOT NULL DEFAULT 'ASSIGNED' COMMENT '通知动作',
  title VARCHAR(128) NOT NULL COMMENT '通知标题',
  content VARCHAR(512) DEFAULT NULL COMMENT '通知内容',
  read_status VARCHAR(16) NOT NULL DEFAULT 'UNREAD' COMMENT '读取状态',
  read_at DATETIME DEFAULT NULL COMMENT '读取时间',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT DEFAULT 0 COMMENT '更新人',
  KEY idx_wom_notify_recipient (recipient_user_id, read_status, deleted, created_at),
  KEY idx_wom_notify_work_order (work_order_id, deleted)
) COMMENT='工单小程序派单通知';
