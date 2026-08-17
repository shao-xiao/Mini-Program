-- Keep resolution evidence append-only and separate from the original inspection photos.
CREATE TABLE inspection_history_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    inspection_history_id BIGINT NOT NULL COMMENT 'Inspection history entry ID',
    file_id BIGINT NOT NULL COMMENT 'Protected file ID',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Display order',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    created_by BIGINT NULL COMMENT 'Creator',
    updated_by BIGINT NULL COMMENT 'Updater',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inspection_history_attachment_file (inspection_history_id, file_id, deleted),
    KEY idx_inspection_history_attachment_history (inspection_history_id, deleted),
    KEY idx_inspection_history_attachment_file (file_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Inspection resolution photos attached to an append-only history entry';
